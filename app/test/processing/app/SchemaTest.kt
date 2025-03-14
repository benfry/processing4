package processing.app

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test


class SchemaTest {
    private val base: Base = mock<Base>{

    }
    companion object {
        val preferences: MockedStatic<Preferences> = mockStatic(Preferences::class.java)
    }


    @Test
    fun testLocalFiles() {
        val file = "/this/is/a/local/file"
        Schema.handleSchema("pde://$file", base)
        verify(base).handleOpen(file)
    }

    @Test
    fun testNewSketch() {
        Schema.handleSchema("pde://sketch/new", base)
        verify(base).handleNew()
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testBase64SketchAndExtraFiles() {
        val sketch = """
        void setup(){
        
        }
        void draw(){
        
        }
        """.trimIndent()

        val base64 = Base64.encode(sketch.toByteArray())
        Schema.handleSchema("pde://sketch/base64/$base64?pde=AnotherFile:$base64", base)
        val captor = ArgumentCaptor.forClass(String::class.java)

        verify(base).handleOpenUntitled(captor.capture())

        val file = File(captor.value)
        assert(file.exists())
        assert(file.readText() == sketch)

        val extra = file.parentFile.resolve("AnotherFile.pde")
        assert(extra.exists())
        assert(extra.readText() == sketch)
        file.parentFile.deleteRecursively()
    }

    @Test
    fun testURLSketch() {
        Schema.handleSchema("pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/Array/Array.pde", base)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(base).handleOpenUntitled(captor.capture())
        val output = File(captor.value)
        assert(output.exists())
        assert(output.name == "Array.pde")
        assert(output.extension == "pde")
        assert(output.parentFile.name == "Array")

        output.parentFile.parentFile.deleteRecursively()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "Module.pde:https://github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/ArrayObjects/Module.pde",
        "Module.pde",
        "Module:Module.pde",
        "Module:https://github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/ArrayObjects/Module.pde",
        "Module.pde:github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/ArrayObjects/Module.pde"
    ])
    fun testURLSketchWithFile(file: String){
        Schema.handleSchema("pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/ArrayObjects/ArrayObjects.pde?pde=$file", base)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(base).handleOpenUntitled(captor.capture())

        // wait for threads to resolve
        Thread.sleep(1000)

        val output = File(captor.value)
        assert(output.parentFile.name == "ArrayObjects")
        assert(output.exists())
        assert(output.parentFile.resolve("Module.pde").exists())
        output.parentFile.parentFile.deleteRecursively()
    }

    @Test
    fun testPreferences() {
        Schema.handleSchema("pde://preferences?test=value", base)
        preferences.verify {
            Preferences.set("test", "value")
            Preferences.save()
        }
    }
}