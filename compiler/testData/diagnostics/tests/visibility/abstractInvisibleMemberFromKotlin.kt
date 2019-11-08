// MODULE: base
// FILE: Base.kt
package base

abstract class BaseClass {
    fun foo(): String {
        return internalFoo()
    }
    internal abstract fun internalFoo(): String
}

// MODULE: impl(base)
// FILE: Impl.kt
package impl
import base.*

<!SUPER_CLASS_HAS_INVISIBLE_ABSTRACT_MEMBER!>class BaseClassImpl : BaseClass()<!>

fun foo() {
    BaseClassImpl().foo()
}
