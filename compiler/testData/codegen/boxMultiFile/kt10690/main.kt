import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

fun box(): String {
    if (::box.javaMethod?.kotlinFunction == null)
        return "Fail box"
    if (::test1.javaMethod?.kotlinFunction == null)
        return "Fail test1"
    if (::test2.javaMethod?.kotlinFunction == null)
        return "Fail test2"

    return "OK"
}

fun test1() {
}
