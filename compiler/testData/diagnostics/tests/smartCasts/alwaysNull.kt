fun foo(): String {
    var s: String?
    s = null
    <!DEBUG_INFO_CONSTANT!>s<!>?.length
    <!ALWAYS_NULL!>s<!><!UNSAFE_CALL!>.<!>length
    if (<!SENSELESS_COMPARISON!><!DEBUG_INFO_CONSTANT!>s<!> == null<!>) return <!ALWAYS_NULL!>s<!>!!
    var t: String? = "y"
    if (t == null) t = "x"
    var x: Int? = null
    if (x == null) <!TYPE_MISMATCH!>x += null<!>
    return <!DEBUG_INFO_SMARTCAST!>t<!> + s
}

fun String?.gav() {}

fun bar(s: String?) {
    if (s != null) return
    // Ideally we should have DEBUG_INFO_CONSTANT instead
    <!ALWAYS_NULL!>s<!>.gav()
    <!DEBUG_INFO_CONSTANT!>s<!> as? String
    <!DEBUG_INFO_CONSTANT!>s<!> <!USELESS_CAST!>as String?<!>
    <!ALWAYS_NULL!>s<!> as String
}