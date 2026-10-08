package np.com.sijandhakal.petcare.dashboard.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.Executors

/**
 * Turns typed addresses into map positions and map positions back into addresses, using the
 * device's built-in Geocoder (no API key needed). Results are always delivered on the main thread;
 * null means nothing was found or no geocoder is available (e.g. offline).
 */
object PlaceGeocoder {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun locate(context: Context, address: String, onResult: (LatLng?) -> Unit) {
        lookup(context, { found -> onResult(found?.let { LatLng(it.latitude, it.longitude) }) }) { geocoder, deliver ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(address, 1, listener(deliver))
            } else {
                @Suppress("DEPRECATION")
                deliver(geocoder.getFromLocationName(address, 1)?.firstOrNull())
            }
        }
    }

    fun describe(context: Context, position: LatLng, onResult: (String?) -> Unit) {
        lookup(context, { found -> onResult(found?.getAddressLine(0)) }) { geocoder, deliver ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(position.latitude, position.longitude, 1, listener(deliver))
            } else {
                @Suppress("DEPRECATION")
                deliver(geocoder.getFromLocation(position.latitude, position.longitude, 1)?.firstOrNull())
            }
        }
    }

    private fun listener(deliver: (Address?) -> Unit) = object : Geocoder.GeocodeListener {
        override fun onGeocode(addresses: MutableList<Address>) = deliver(addresses.firstOrNull())
        override fun onError(errorMessage: String?) = deliver(null)
    }

    /** Runs [query] off the main thread; whatever it passes to `deliver` reaches [onResult] on the main thread. */
    private fun lookup(
        context: Context,
        onResult: (Address?) -> Unit,
        query: (geocoder: Geocoder, deliver: (Address?) -> Unit) -> Unit
    ) {
        val deliver: (Address?) -> Unit = { address -> mainHandler.post { onResult(address) } }
        if (!Geocoder.isPresent()) {
            deliver(null)
            return
        }
        val geocoder = Geocoder(context.applicationContext)
        executor.execute {
            try {
                query(geocoder, deliver)
            } catch (e: Exception) {
                deliver(null)
            }
        }
    }
}
