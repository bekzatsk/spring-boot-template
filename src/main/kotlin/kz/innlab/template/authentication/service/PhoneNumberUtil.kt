package kz.innlab.template.authentication.service

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil as LibPhoneNumberUtil

/**
 * Normalizes a phone number to E.164 format.
 * Requires '+' prefix (no default region — eliminates ambiguity).
 * @throws IllegalArgumentException if phone number is invalid or missing '+' prefix
 */
fun normalizeToE164(rawPhone: String): String {
    if (!rawPhone.startsWith("+")) {
        throw IllegalArgumentException("Phone number must start with '+' country code prefix")
    }
    val util = LibPhoneNumberUtil.getInstance()
    val parsed = try {
        util.parse(rawPhone, null)  // null region since '+' prefix provides country code
    } catch (e: NumberParseException) {
        throw IllegalArgumentException("Invalid phone number: $rawPhone")
    }
    if (!util.isValidNumber(parsed)) {
        throw IllegalArgumentException("Phone number not valid: $rawPhone")
    }
    return util.format(parsed, LibPhoneNumberUtil.PhoneNumberFormat.E164)
}
