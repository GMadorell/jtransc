/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.jtransc.AllBuild
import com.jtransc.ast.AstBuildSettings
import com.jtransc.gen.haxe.HaxeGenDescriptor
import com.jtransc.maven.MavenLocalRepository
import com.jtransc.util.ClassUtils
import javatest.KotlinCollections
import javatest.lang.BasicTypesTest
import javatest.lang.StringsTest
import javatest.lang.SystemTest
import jtransc.JTranscVersion
import jtransc.annotation.ClassMembersTest
import jtransc.annotation.MethodBodyTest
import jtransc.bug.*
import jtransc.java8.Java8Test
import jtransc.rt.test.*
import org.junit.Assert
import org.junit.Test
import java.io.File
import javax.script.ScriptEngineManager

class HaxeGenSuite {

	//-----------------------------------------------------------------
	// Java Lang

	//@Test fun langBasicTypesTest() = testClass<BasicTypesTest>()
	@Test fun langStringsTest() = testClass<StringsTest>()

	@Test fun langSystemTest() = testClass<SystemTest>() { it.replace(
		"java.runtime.name:Java(TM) SE Runtime Environment", "java.runtime.name:jtransc-haxe"
	).replace(
		"path.separator:;", "path.separator::"
	) }

	//-----------------------------------------------------------------
	// Java Utils

	//@Test fun utilsCollectionsTest() = testClass<CollectionsTest>()

	//-----------------------------------------------------------------
	// Kotlin Collections
	@Test fun kotlinCollectionsTest() = testClass<KotlinCollections>()

	@Test fun stringsTest() = testClass<JTranscStringTest>()
	@Test fun arrayListTest() = testClass<JTranscCollectionsTest>()
	@Test fun stringBuilderTest() = testClass<StringBuilderTest>()
	@Test fun stackTraceTest() = testClass<JTranscStackTraceTest>()
	@Test fun reflectionTest() = testClass<JTranscReflectionTest>()
	@Test fun nioTest() = testClass<JTranscNioTest>()
	@Test fun arithmeticTest() = testClass<JTranscArithmeticTest>()

	@Test fun basicTypesTest() = testClass<BasicTypesTest>()

	@Test fun bug12Test() = testClass<JTranscBug12Test>()
	@Test fun bug12Test2() = testClass<JTranscBug12Test2>()
	@Test fun bug14Test() = testClass<JTranscBug14Test>()
	@Test fun bugArrayGetClass() = testClass<JTranscBugArrayGetClass>()
	@Test fun bugArrayDynamicInstantiate() = testClass<JTranscBugArrayDynamicInstantiate>()

	@Test fun bugAbstractInheritance1() = testClass<JTranscBugAbstractInheritance1>()
	@Test fun bugAbstractInheritance2() = testClass<JTranscBugAbstractInheritance2>()

	@Test fun bugClassRefTest() = testClass<JTranscBugClassRefTest>()

	@Test fun regressionTest1() = testClass<JTranscRegression1Test>()
	@Test fun regressionTest2() = testClass<JTranscRegression2Test>()
	@Test fun regressionTest3() = testClass<JTranscRegression3Test>()

	@Test fun java8Test() = testClass<Java8Test>()

	@Test fun methodBodyTest() = Assert.assertEquals("INT:777", runClass<MethodBodyTest>().trim())
	@Test fun classMembersTest() = Assert.assertEquals("mult:246", runClass<ClassMembersTest>().trim())

	// Shortcut
	inline fun <reified T : Any> testClass() = testClass(T::class.java, { it })
	inline fun <reified T : Any> testClass(noinline transformer: (String) -> String) = testClass(T::class.java, transformer)

	val kotlinPaths = listOf<String>() +
		MavenLocalRepository.locateJars("org.jetbrains.kotlin:kotlin-runtime:1.0.0") +
		MavenLocalRepository.locateJars("org.jetbrains.kotlin:kotlin-stdlib:1.0.0") +
		MavenLocalRepository.locateJars("org.jetbrains.kotlin:kotlin-reflect:1.0.0")
	val testClassesPath = File("target/test-classes").absolutePath

	fun <T : Any> testClass(clazz: Class<T>, transformer: (String) -> String) {
		val expected = transformer(ClassUtils.callMain(clazz))
		val result = runClass(clazz)

		Assert.assertEquals(normalize(expected), normalize(result))
	}

	fun normalize(str:String) = str.replace("\r\n", "\n").replace('\r', '\n')

	inline fun <reified T : Any> runClass(): String {
		return runClass(T::class.java)
	}

	fun <T : Any> runClass(clazz: Class<T>): String {
		val build = AllBuild(
			target = HaxeGenDescriptor,
			classPaths = listOf(testClassesPath) + kotlinPaths,
			entryPoint = clazz.name,
			output = "program.haxe.js", subtarget = "js",
			//output = "program.haxe.cpp", subtarget = "cpp",
			targetDirectory = System.getProperty("java.io.tmpdir")
		)
		return build.buildAndRunCapturingOutput(AstBuildSettings(jtranscVersion = JTranscVersion.getVersion(), debug = false)).output
	}

	val engine = ScriptEngineManager().getEngineByMimeType("text/javascript")
	@Test
	fun testExecJsTest1() {
		Assert.assertEquals(10, engine.eval("(function() { return 10; })()"));
	}

	@Test
	fun testExecJsTest2() {
		Assert.assertEquals(10, engine.eval("(function() { return 10; })()"));
	}
}
