# Form Page Layout Skill

## Purpose

Generate Android XML layouts for form-based pages using the provided layout conventions and Material Components patterns.

## Core Rules

- Use `TextInputLayout` together with `TextInputEditText` for every user-entry field.
- Use an input type appropriate to the field.
- Use `Enter {Field Name}` as the `TextInputLayout` hint for entry fields.
- Give every widget a concise, descriptive ID using a widget-type prefix.
- Prefer `ConstraintLayout` as the root layout for responsive form pages.
- Use `0dp` width with start/end constraints for fields and full-width buttons.
- Maintain consistent spacing and margins between form elements.
- Prefer Material Components for form controls and buttons.
- Keep the XML organized with comments separating logical sections.

## Input Type Guidelines

Use the most relevant `android:inputType` for each field:

| Field | Input Type |
|---|---|
| Email | `textEmailAddress` |
| Password | `textPassword` |
| Name | `textPersonName` |
| Username | `text` |
| Phone Number | `phone` |
| Number | `number` |
| Decimal Number | `numberDecimal` |
| URL | `textUri` |
| Multiline Text | `textMultiLine` |
| Date | `date` |
| Time | `time` |
| Address | `textPostalAddress` |

## Hint Convention

Always use the `Enter {Field Name}` convention for fields that require user input.

Examples:

- `Enter Email`
- `Enter Password`
- `Enter Full Name`
- `Enter Phone Number`
- `Enter Address`

Do not use vague hints such as `Input`, `Value`, or `Type here` when the field name can be specified.

## Widget ID Convention

Use the following prefixes:

| Widget | Prefix | Example |
|---|---|---|
| Button | `btn_` | `btn_login` |
| TextInputLayout | `til_` | `til_email` |
| TextInputEditText | `tiet_` | `tiet_email` |
| TextView | `tv_` | `tv_title` |
| ImageView | `iv_` | `iv_logo` |
| CheckBox | `cb_` | `cb_remember` |
| RadioButton | `rb_` | `rb_male` |
| Spinner | `sp_` | `sp_country` |
| RecyclerView | `rv_` | `rv_users` |
| Switch | `sw_` | `sw_notifications` |
| ProgressBar | `pb_` | `pb_loading` |

IDs should be short, descriptive, lowercase, and use underscores.

## TextInputLayout Rules

For form fields, use the Material Components outlined style unless the user specifies another style:

```xml
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_email"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:hint="Enter Email">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/tiet_email"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textEmailAddress"
        android:maxLines="1" />

</com.google.android.material.textfield.TextInputLayout>
```

## Password Fields

For password fields:

- Use `android:inputType="textPassword"`.
- Use `app:endIconMode="password_toggle"` so the user can show or hide the password.
- Use a `til_` ID for the `TextInputLayout` and a corresponding `tiet_` ID for the input.

Example:

```xml
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_password"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:hint="Enter Password"
    app:endIconMode="password_toggle">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/tiet_password"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword"
        android:maxLines="1" />

</com.google.android.material.textfield.TextInputLayout>
```

## Buttons

Use `MaterialButton` for primary form actions.

- Use a `btn_` prefix.
- Give the button a descriptive action name.
- Use `android:text` for the action label.
- Use responsive `0dp` width with start/end constraints when using `ConstraintLayout`.
- Use a consistent corner radius and spacing unless the design specifies otherwise.

Example:

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_login"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:text="Login"
    app:cornerRadius="8dp" />
```

## Layout Structure

For a typical form page:

1. Create a `ConstraintLayout` root.
2. Add branding or header content if required.
3. Add form fields in logical order.
4. Use `TextInputLayout` + `TextInputEditText` for entry fields.
5. Add action buttons after the related fields.
6. Apply consistent margins and constraints.
7. Add accessibility attributes where appropriate.

Example ordering for a login page:

```text
Logo
  ↓
Email
  ↓
Password
  ↓
Login Button
```

## Responsive Layout Rules

- Avoid hard-coded screen-width values for form controls.
- Use `android:layout_width="0dp"` with constraints for elements that should span the available width.
- Use start/end margins instead of relying only on left/right margins.
- Keep the form visually centered and usable on different screen sizes.
- Avoid excessive fixed heights for input fields.
- Use `wrap_content` for normal form controls unless a fixed height is explicitly required.

## Accessibility

- Add meaningful `android:contentDescription` values to informative images.
- Do not add unnecessary content descriptions to purely decorative images.
- Use clear, human-readable field hints.
- Ensure buttons have meaningful action text.

## XML Quality

- Include the required Android and Material namespaces.
- Keep related attributes grouped logically.
- Use comments to identify major form sections.
- Keep IDs consistent between the `TextInputLayout` and its child `TextInputEditText`.
- Do not introduce unnecessary widgets when an existing Material Component satisfies the requirement.
- Follow the user's supplied reference layout when specific styling or positioning is provided.

## Generation Behavior

When given a form/page description:

1. Identify every field requiring user input.
2. Select the correct Android input type for each field.
3. Create a `TextInputLayout` and `TextInputEditText` pair for each entry field.
4. Set the hint to `Enter {Field Name}`.
5. Generate concise widget IDs using the appropriate prefixes.
6. Add suitable form actions using `MaterialButton`.
7. Build the page using responsive `ConstraintLayout` constraints.
8. Preserve any explicitly requested icons, colors, spacing, dimensions, or component styles.
9. Return complete, valid Android XML unless the user asks for only a specific portion.

## Reference Pattern

The supplied reference establishes the preferred general pattern:

- `ConstraintLayout` root
- Material `TextInputLayout`
- Material `TextInputEditText`
- Outlined text fields
- `Enter {Field Name}` hints
- Concise widget IDs
- Responsive start/end constraints
- `MaterialButton` for actions
- Password visibility toggle for password fields
