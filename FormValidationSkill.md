# Form Validation Skill

This document outlines the validation logic implemented for the Login screen in the Appify project.

## Email Validation
- **Requirement**: Must not be empty.
- **Requirement**: Must follow a valid email format.
- **Implementation**:
    - Checked using `email.isEmpty()`.
    - Format verified using `android.util.Patterns.EMAIL_ADDRESS`.
- **Feedback**: Toast messages "Email is empty" or "Invalid email format".

## Password Validation
- **Requirement**: Must not be empty.
- **Requirement**: Must meet the security policy:
    - 8 to 30 characters long.
    - At least one uppercase letter.
    - At least one number.
    - At least one special character.
- **Implementation**:
    - Checked using `password.isEmpty()`.
    - Policy verified using a Regular Expression (Regex).
- **Regex**: `^(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!/\\*()_\-.,:;{}|\[\]<>?~`]).{8,30}$`
    - `(?=.*[A-Z])`: Lookahead for at least one uppercase letter.
    - `(?=.*[0-9])`: Lookahead for at least one digit.
    - `(?=.*[@#$%^&+=!...])`: Lookahead for at least one special character.
    - `.{8,30}`: Matches between 8 and 30 characters of any type.
- **Feedback**: Toast messages "Password is empty" or a detailed policy message.

## Post-Validation
If both fields pass validation, the application proceeds to the server connection logic (currently marked as "To do").
