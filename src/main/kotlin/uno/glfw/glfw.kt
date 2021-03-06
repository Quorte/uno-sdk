package uno.glfw

import glm_.BYTES
import kool.adr
import glm_.i
import glm_.vec2.Vec2i
import gln.debug.glDebugCallback
import kool.stak
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWNativeWin32
import org.lwjgl.glfw.GLFWVidMode
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.APIUtil
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.VkInstance
import uno.glfw.windowHint.Profile
import uno.kotlin.parseInt
import vkk.VK_CHECK_RESULT
import vkk.entities.VkSurface
//import vkk.VK_CHECK_RESULT
//import vkk.VkSurfaceKHR
//import vkk.adr
import java.util.function.BiPredicate

/**
 * Created by elect on 22/04/17.
 */

object glfw {

    /** Short version of:
     *  glfw.init()
     *  glfw.windowHint {
     *      context.version = "3.2"
     *      windowHint.profile = "core"
     *  }
     *  + default error callback
     */
    @Throws(RuntimeException::class)
    fun init(version: String, profile: Profile = Profile.core, installDefaultErrorCallback: Boolean = true) {
        init(installDefaultErrorCallback)
        windowHint {
            context.version = version
            val v = version[0].parseInt() * 10 + version[1].parseInt()
            if (v >= 32) // The concept of a core profile does not exist prior to OpenGL 3.2
                this.profile = profile
        }
    }

    @Throws(RuntimeException::class)
    fun init(errorCallback: GLFWErrorCallbackT) {
        this.errorCallback = errorCallback
        init()
    }

    @Throws(RuntimeException::class)
    fun init(installDefaultErrorCallback: Boolean) {
        if (installDefaultErrorCallback)
            errorCallback = defaultErrorCallback
        init()
    }

    @Throws(RuntimeException::class)
    fun init() {

        if (!glfwInit())
            throw RuntimeException("Unable to initialize GLFW")

        // This window hint is required to use OpenGL 3.1+ on macOS
        if (Platform.get() == Platform.MACOSX)
            windowHint.forwardComp = true
    }

    fun terminate() {
        glfwTerminate()
        nErrorCallback.free()
        glDebugCallback?.free()
    }

    val version: String
        get() = glfwGetVersionString()

    private val ERROR_CODES: MutableMap<Int, String> = APIUtil.apiClassTokens(BiPredicate { _, value -> value in 65537..131071 }, null, org.lwjgl.glfw.GLFW::class.java)
    val nErrorCallback: GLFWErrorCallback = GLFWErrorCallback.create { error, description -> errorCallback?.invoke(ERROR_CODES[error]!!, memUTF8(description)) }
    val defaultErrorCallback: GLFWErrorCallbackT = { error, description -> System.err.println("glfw $error error: $description") }

    var errorCallback: GLFWErrorCallbackT? = null
        set(value) {
            if (value != null) {
                field = value
                nglfwSetErrorCallback(nErrorCallback.address()) // TODO adr
            } else
                nglfwSetErrorCallback(NULL)
        }

    val vulkanSupported: Boolean
        get() = GLFWVulkan.glfwVulkanSupported()

    val time: Double
        get() = glfwGetTime()

    val primaryMonitor: GlfwMonitor
        get() = glfwGetPrimaryMonitor()

    /** videoMode of primaryMonitor */
    val videoMode: GLFWVidMode
        get() = glfwGetVideoMode(primaryMonitor)!!

    fun videoMode(monitor: GlfwMonitor): GLFWVidMode? = glfwGetVideoMode(monitor)

    val resolution: Vec2i
        get() = Vec2i(videoMode.width, videoMode.height)

    var swapInterval: VSync = VSync.ON
        set(value) {
            glfwSwapInterval(value.i)
            field = value
        }

    fun pollEvents() = glfwPollEvents()

    val requiredInstanceExtensions: ArrayList<String>
        get() = stak {
            val pCount = it.nmalloc(1, Int.BYTES)
            val ppNames = GLFWVulkan.nglfwGetRequiredInstanceExtensions(pCount)
            val count = memGetInt(pCount)
            val pNames = MemoryUtil.memPointerBuffer(ppNames, count) ?: return arrayListOf()
            val res = ArrayList<String>(count)
            for (i in 0 until count)
                res += MemoryUtil.memASCII(pNames[i])
            return res
        }

    fun createWindowSurface(windowHandle: GlfwWindowHandle, instance: VkInstance): VkSurface =
            VkSurface(stak.longAddress { surface ->
                VK_CHECK_RESULT(GLFWVulkan.nglfwCreateWindowSurface(instance.adr, windowHandle.L, NULL, surface))
            })

    fun attachWin32Window(handle: HWND): GlfwWindowHandle = GlfwWindowHandle(GLFWNativeWin32.glfwAttachWin32Window(handle.L, NULL))

    enum class Error(val i: Int) {
        none(GLFW_NO_ERROR),
        notInitialized(0x00010001),
        noCurrentContext(0x00010002),
        invalidEnum(0x00010003),
        invalidValue(0x00010004),
        outOfMemory(0x00010005),
        apiUnavailable(0x00010006),
        versionUnavailable(0x00010007),
        platformError(0x00010008),
        formatUnavailable(0x00010009);

        companion object {
            infix fun of(i: Int) = values().first { it.i == i }
        }
    }

    val error: Error
        get() = stak {
            val pointer = it.mallocPointer(1)
            val code = glfwGetError(pointer)
            errorDescription = when {
                code != GLFW_NO_ERROR -> memUTF8(pointer[0])
                else -> ""
            }
            return Error of pointer[0].i
        }
    var errorDescription = ""

    fun <T> initHint(block: initHint.() -> T) = initHint.block()
    fun <T> windowHint(block: windowHint.() -> T) = windowHint.block()
}