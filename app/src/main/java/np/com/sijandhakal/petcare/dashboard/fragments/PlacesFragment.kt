package np.com.sijandhakal.petcare.dashboard.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.dashboard.data.PetCareRepository
import np.com.sijandhakal.petcare.dashboard.data.Place
import np.com.sijandhakal.petcare.dashboard.data.PlaceCategory
import np.com.sijandhakal.petcare.dashboard.data.PlaceGeocoder
import np.com.sijandhakal.petcare.databinding.DialogPlaceFormBinding
import np.com.sijandhakal.petcare.databinding.FragmentPlacesBinding
import np.com.sijandhakal.petcare.databinding.ItemPlaceBinding

/**
 * "Places" tab: the user's geotagged vets, parks, groomers and stores on a Google map.
 * Long-press the map to tag a spot; typed addresses are geocoded to a map position on save.
 */
class PlacesFragment : Fragment() {

    private var _binding: FragmentPlacesBinding? = null
    private val binding get() = _binding!!

    private var places: List<Place> = emptyList()
    private var category: PlaceCategory? = null
    private var listener: ListenerRegistration? = null
    private var map: GoogleMap? = null
    private val markers = mutableMapOf<String, Marker>()
    private var cameraFitted = false
    private val adapter = PlaceAdapter(::focusOnMap, ::openInMaps, ::showPlaceForm, ::deletePlace)

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { enableMyLocation() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? DashboardActivity)?.bindTopBar(binding.topBar, R.string.nav_places)

        PlaceCategory.entries.forEach { placeCategory ->
            binding.chipGroupCategory.addView(categoryChip(binding.chipGroupCategory, placeCategory))
        }
        binding.chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            category = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it).tag as? PlaceCategory }
            cameraFitted = false
            render()
        }
        binding.tietSearch.doAfterTextChanged { render() }
        binding.fabTagPlace.setOnClickListener { showPlaceForm(null) }

        binding.rvPlaces.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaces.adapter = adapter

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            if (_binding == null) return@getMapAsync
            map = googleMap
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isMapToolbarEnabled = false
            // Long-press anywhere to tag that spot; tap a pin's label to get directions.
            googleMap.setOnMapLongClickListener { position -> tagPlaceAt(position) }
            googleMap.setOnInfoWindowClickListener { marker -> (marker.tag as? Place)?.let(::openInMaps) }
            enableMyLocation()
            render()
        }
        requestLocationPermissionIfNeeded()

        listener = PetCareRepository.listenPlaces(::showError) { result ->
            places = result
            render()
        }
    }

    private fun categoryChip(group: ViewGroup, placeCategory: PlaceCategory): Chip {
        val chip = layoutInflater.inflate(R.layout.view_filter_chip, group, false) as Chip
        chip.id = View.generateViewId()
        chip.tag = placeCategory
        chip.setText(placeCategory.label)
        chip.setChipIconResource(placeCategory.icon)
        chip.isChipIconVisible = true
        return chip
    }

    private fun render() {
        val b = _binding ?: return
        val query = b.tietSearch.text?.toString()?.trim().orEmpty()
        val visible = places.filter { place ->
            (category == null || place.category == category) &&
                (query.isEmpty() || place.name.contains(query, ignoreCase = true) ||
                    place.address.contains(query, ignoreCase = true))
        }
        adapter.submitList(visible)
        b.tvPlacesCount.text = resources.getQuantityString(R.plurals.saved_places, places.size, places.size)
        b.tvEmpty.isVisible = visible.isEmpty()
        b.tvEmpty.setText(if (places.isEmpty()) R.string.places_empty else R.string.places_no_match)
        showMarkers(visible)
    }

    private fun showMarkers(visible: List<Place>) {
        val googleMap = map ?: return
        markers.values.forEach { it.remove() }
        markers.clear()
        val located = visible.filter { it.hasLocation }
        located.forEach { place ->
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(place.latitude!!, place.longitude!!))
                    .title(place.name)
                    .snippet(place.address.ifBlank { getString(place.category.label) })
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue(place.category)))
            )
            if (marker != null) {
                marker.tag = place
                markers[place.id] = marker
            }
        }
        // Frame all the pins once per load/filter change, not on every Firestore update.
        if (!cameraFitted && located.isNotEmpty()) {
            cameraFitted = true
            val update = if (located.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(located[0].latitude!!, located[0].longitude!!), 15f)
            } else {
                val bounds = LatLngBounds.builder()
                located.forEach { bounds.include(LatLng(it.latitude!!, it.longitude!!)) }
                CameraUpdateFactory.newLatLngBounds(bounds.build(), resources.getDimensionPixelSize(R.dimen.map_padding))
            }
            binding.root.post { if (_binding != null) googleMap.moveCamera(update) }
        }
    }

    private fun markerHue(category: PlaceCategory) = when (category) {
        PlaceCategory.VET -> BitmapDescriptorFactory.HUE_RED
        PlaceCategory.PARK -> BitmapDescriptorFactory.HUE_GREEN
        PlaceCategory.GROOMING -> BitmapDescriptorFactory.HUE_VIOLET
        PlaceCategory.STORE -> BitmapDescriptorFactory.HUE_ORANGE
    }

    private fun focusOnMap(place: Place) {
        val googleMap = map
        val marker = markers[place.id]
        if (googleMap == null || marker == null) {
            Toast.makeText(requireContext(), R.string.place_not_on_map, Toast.LENGTH_SHORT).show()
            return
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 16f))
        marker.showInfoWindow()
        binding.scroll.smoothScrollTo(0, 0)
    }

    /** Opens directions in the user's maps app, using the saved pin when there is one. */
    private fun openInMaps(place: Place) {
        val uri = if (place.hasLocation) {
            Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
        } else {
            Uri.parse("geo:0,0?q=" + Uri.encode(place.address.ifBlank { place.name }))
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_maps_app, Toast.LENGTH_SHORT).show()
        }
    }

    /** Long-press on the map: look up the address of that spot, then open the form pinned there. */
    private fun tagPlaceAt(position: LatLng) {
        PlaceGeocoder.describe(requireContext(), position) { address ->
            if (_binding == null) return@describe
            showPlaceForm(Place(address = address.orEmpty(), latitude = position.latitude, longitude = position.longitude))
        }
    }

    /** Tag a new place (when [place] has no id) or edit an existing one. */
    private fun showPlaceForm(place: Place?) {
        val isNew = place == null || place.id.isBlank()
        val form = DialogPlaceFormBinding.inflate(layoutInflater)
        PlaceCategory.entries.forEach { placeCategory ->
            val chip = categoryChip(form.chipGroupCategory, placeCategory)
            form.chipGroupCategory.addView(chip)
            val preselected = if (isNew) category ?: PlaceCategory.VET else place!!.category
            if (placeCategory == preselected) form.chipGroupCategory.check(chip.id)
        }
        form.tietName.setText(place?.name.orEmpty())
        form.tietAddress.setText(place?.address.orEmpty())
        form.tietNotes.setText(place?.notes.orEmpty())
        form.tietName.doAfterTextChanged { form.tilName.error = null }
        if (place?.hasLocation == true) form.tilAddress.helperText = getString(R.string.place_pinned_helper)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isNew) R.string.place_form_title_add else R.string.place_form_title_edit)
            .setView(form.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = form.tietName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                form.tilName.error = getString(R.string.place_name_required)
                return@setOnClickListener
            }
            val address = form.tietAddress.text?.toString()?.trim().orEmpty()
            val draft = Place(
                id = place?.id.orEmpty(),
                name = name,
                category = form.chipGroupCategory.findViewById<Chip>(form.chipGroupCategory.checkedChipId)
                    ?.tag as? PlaceCategory ?: PlaceCategory.VET,
                address = address,
                notes = form.tietNotes.text?.toString()?.trim().orEmpty(),
                latitude = place?.latitude,
                longitude = place?.longitude
            )
            dialog.dismiss()

            // Keep an existing pin unless the address was changed; otherwise geocode the typed address.
            val addressChanged = address != place?.address.orEmpty()
            if (address.isNotEmpty() && (!draft.hasLocation || addressChanged)) {
                PlaceGeocoder.locate(requireContext().applicationContext, address) { position ->
                    val located = if (position != null) {
                        draft.copy(latitude = position.latitude, longitude = position.longitude)
                    } else {
                        context?.let { Toast.makeText(it, R.string.place_address_not_found, Toast.LENGTH_LONG).show() }
                        draft
                    }
                    savePlace(located)
                }
            } else {
                savePlace(draft)
            }
        }
    }

    private fun savePlace(place: Place) {
        cameraFitted = false
        PetCareRepository.savePlace(place).addOnFailureListener(::showError)
    }

    private fun deletePlace(place: Place) {
        PetCareRepository.deletePlace(place.id).addOnFailureListener(::showError)
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.task_deleted, place.name), Snackbar.LENGTH_LONG)
            .setAnchorView(b.fabTagPlace)
            .setAction(R.string.action_undo) { PetCareRepository.savePlace(place) }
            .show()
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    /** Shows the blue "you are here" dot and re-centre button when location access was granted. */
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val googleMap = map ?: return
        if (hasLocationPermission()) googleMap.isMyLocationEnabled = true
    }

    private fun showError(e: Exception) {
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.error_firebase, e.localizedMessage), Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        map = null
        markers.clear()
        _binding = null
    }

    private class PlaceAdapter(
        private val onFocus: (Place) -> Unit,
        private val onDirections: (Place) -> Unit,
        private val onEdit: (Place) -> Unit,
        private val onDelete: (Place) -> Unit
    ) : ListAdapter<Place, PlaceAdapter.ViewHolder>(Diff) {

        class ViewHolder(val binding: ItemPlaceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val place = getItem(position)
            val b = holder.binding
            b.ivIcon.setImageResource(place.category.icon)
            b.tvCategory.setText(place.category.label)
            b.tvName.text = place.name
            b.tvDetails.text = listOf(place.address, place.notes).filter { it.isNotBlank() }.joinToString(" · ")
            b.tvDetails.isVisible = b.tvDetails.text.isNotEmpty()
            b.btnDirections.setOnClickListener { onDirections(place) }
            b.btnDelete.setOnClickListener { onDelete(place) }
            b.root.setOnClickListener { onFocus(place) }
            b.root.setOnLongClickListener { onEdit(place); true }
        }

        private object Diff : DiffUtil.ItemCallback<Place>() {
            override fun areItemsTheSame(oldItem: Place, newItem: Place) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Place, newItem: Place) = oldItem == newItem
        }
    }
}
