package com.jtransc.gen.haxe

import com.jtransc.ast.*
import com.jtransc.error.InvalidOperationException
import com.jtransc.error.invalidOp
import com.jtransc.error.noImpl
import com.jtransc.gen.ClassMappings
import com.jtransc.text.Indenter
import com.jtransc.text.escape
import com.jtransc.text.quote
import com.jtransc.text.toUcFirst
import com.jtransc.util.sortDependenciesSimple
import com.jtransc.vfs.SyncVfsFile
import jtransc.annotation.JTranscKeep
import jtransc.annotation.haxe.*

class GenHaxeGen(
	val program: AstProgram,
	val mappings: ClassMappings,
	val features: AstFeatures,
	val srcFolder: SyncVfsFile,
	val featureSet: Set<AstFeature>
) {
	val names = HaxeNames(program, mappings)
	val refs = References()
	lateinit var clazz: AstClass
	lateinit var method: AstMethod
	lateinit var mutableBody: MutableBody
	lateinit var stm: AstStm

	fun AstStm.gen(): Indenter = gen2(this)
	fun AstExpr.gen(): String = gen2(this)
	fun AstBody.gen(): Indenter = gen2(this)
	fun AstClass.gen(): ClassResult = gen2(this)

	internal fun _write(): GenHaxe.ProgramInfo {
		val vfs = srcFolder
		for (clazz in program.classes.filter { !it.isNative }) {
			if (clazz.implCode != null) {
				vfs[clazz.name.haxeFilePath] = clazz.implCode!!
			} else {
				val result = clazz.gen()
				for (file in result.files) {
					val (clazzName, content) = file
					vfs[clazzName.haxeFilePath] = content.toString()
				}
			}
		}

		val copyFiles = program.classes.flatMap {
			it.annotations[HaxeAddFiles::value]?.toList() ?: listOf()
		}

		for (file in copyFiles) {
			vfs[file] = program.resourcesVfs[file]
		}

		val mainClassFq = program.entrypoint
		val mainClass = mainClassFq.haxeClassFqName
		val mainMethod = "main__Ljava_lang_String__V"

		val entryPointClass = FqName(mainClassFq.fqname + "_EntryPoint")
		val entryPointFilePath = entryPointClass.haxeFilePath
		val entryPointFqName = entryPointClass.haxeGeneratedFqName
		val entryPointSimpleName = entryPointClass.haxeGeneratedSimpleClassName
		val entryPointPackage = entryPointFqName.packagePath

		fun calcClasses(program: AstProgram, mainClass: AstClass): List<AstClass> {
			return sortDependenciesSimple(mainClass) {
				it.classDependencies.map { program[it] }
			}
		}

		fun inits() = Indenter.gen {
			line("haxe.CallStack.callStack();")
			when (GenHaxe.INIT_MODE) {
				InitMode.START_OLD -> line("$mainClass.__hx_static__init__();")
				InitMode.START -> {
					for (clazz in calcClasses(program, program[mainClassFq])) {
						line(clazz.name.haxeClassFqNameInt + ".__hx_static__init__();")
					}
				}
				else -> {

				}
			}
		}

		val customMain = program.classes
			.map { it.annotations[HaxeCustomMain::value] }
			.filterNotNull()
			.firstOrNull()

		val plainMain = Indenter.genString {
			line("package \$entryPointPackage;")
			line("class \$entryPointSimpleName") {
				line("static public function main()") {
					line("\$inits")
					line("\$mainClass.\$mainMethod(HaxeNatives.strArray(HaxeNatives.args()));")
				}
			}
		}

		val realMain = customMain ?: plainMain

		vfs[entryPointFilePath] = Indenter.replaceString(realMain, mapOf(
			"entryPointPackage" to entryPointPackage,
			"entryPointSimpleName" to entryPointSimpleName,
			"mainClass" to mainClass,
			"mainMethod" to mainMethod,
			"inits" to inits().toString()
		))

		vfs["HaxeReflectionInfo.hx"] = Indenter.genString {
			fun AstType.REF.getAnnotationProxyName(program: AstProgram): String {
				return "AnnotationProxy_${this.name.haxeGeneratedFqName.fqname.replace('.', '_')}"
			}

			val annotationProxyTypes = Indenter.genString {
				val annotationTypes = program.allAnnotations.map { it.type }.distinct()
				for (at in annotationTypes) {
					val clazz = program[at.name]
					//at.name
					line("// annotation type: $at")
					line("class ${clazz.astType.getAnnotationProxyName(program)} extends jtransc.internal_.JTranscAnnotationBase_ implements ${clazz.name.haxeClassFqName}") {
						line("private var _data:Array<Dynamic>;")
						line("public function new(_data:Dynamic = null) { super(); this._data = _data; }")

						line("public function annotationType__Ljava_lang_Class_():java_.lang.Class_ { return HaxeNatives.resolveClass(${clazz.fqname.quote()}); }")
						line("override public function getClass__Ljava_lang_Class_():java_.lang.Class_ { return HaxeNatives.resolveClass(${clazz.fqname.quote()}); }")
						for ((index, m) in clazz.methods.withIndex()) {
							line("public function ${m.haxeName}():${m.methodType.ret.haxeTypeTag} { return this._data[$index]; }")
						}
					}
				}
			}

			fun annotation(a: AstAnnotation): String {
				fun escapeValue(it: Any?): String {
					return when (it) {
						null -> "null"
						is AstAnnotation -> annotation(it)
						is Pair<*, *> -> escapeValue(it.second)
						is AstFieldRef -> it.containingTypeRef.name.haxeClassFqName + "." + it.haxeName
						is String -> "HaxeNatives.str(${it.quote()})"
						is Int -> "HaxeNatives.int($it)"
						is Long -> "HaxeNatives.long($it)"
						is Float -> "HaxeNatives.float($it)"
						is Double -> "HaxeNatives.double($it)"
						is List<*> -> "[" + it.map { escapeValue(it) }.joinToString(", ") + "]"
						else -> throw InvalidOperationException("Can't handle value ${it.javaClass.name} : $it")
					}
				}
				//val itStr = a.elements.map { it.key.quote() + ": " + escapeValue(it.value) }.joinToString(", ")
				val annotation = program[a.type.classRef]
				val itStr = annotation.methods.map {
					if (it.name in a.elements) {
						escapeValue(a.elements[it.name]!!)
					} else {
						escapeValue(it.defaultTag)
					}
				}.joinToString(", ")
				return "new ${a.type.getAnnotationProxyName(program)}([$itStr])"
			}

			fun annotationInit(a: AstAnnotation): List<AstType.REF> {
				fun escapeValue(it: Any?): List<AstType.REF> {
					return when (it) {
						null -> listOf()
						is AstAnnotation -> annotationInit(it)
						is Pair<*, *> -> escapeValue(it.second)
						is AstFieldRef -> listOf(it.containingTypeRef)
						is List<*> -> it.flatMap { escapeValue(it) }
						else -> listOf()
					}
				}
				//val itStr = a.elements.map { it.key.quote() + ": " + escapeValue(it.value) }.joinToString(", ")
				val annotation = program[a.type.classRef]
				return annotation.methods.flatMap {
					if (it.name in a.elements) {
						escapeValue(a.elements[it.name]!!)
					} else {
						escapeValue(it.defaultTag)
					}
				}
			}

			fun annotations(annotations: List<AstAnnotation>): String {
				return "[" + annotations.map { annotation(it) }.joinToString(", ") + "]"
			}

			fun annotationsInit(annotations: List<AstAnnotation>):Indenter {
				return Indenter.gen {
					for (i in annotations.flatMap { annotationInit(it) }.toHashSet()) {
						line("${i.name.haxeGeneratedFqName}.__hx_static__init__();")
					}
				}
			}

			line("class HaxeReflectionInfo") {
				val classes = program.classes.sortedBy { it.fqname }
				val classToId = classes.withIndex().map { Pair(it.value, it.index) }.toMap()

				line("static public function __initClass(c:java_.lang.Class_):Bool") {
					line("var cn = c.name._str;")
					line("if (cn.substr(0, 1) == '[') return true;")
					line("if (cn == 'V' || cn == 'B' || cn == 'C' || cn == 'S' || cn == 'I' || cn == 'L' || cn == 'J') return true;")
					line("switch (cn.length)") {
						for (clazzGroup in program.classes.groupBy { it.fqname.length }.toList().sortedBy { it.first }) {
							val length = clazzGroup.first
							val classesWithLength = clazzGroup.second
							line("case $length:")
							indent {
								for (clazz in classesWithLength.sortedBy { it.fqname }) {
									val index = classToId[clazz]
									line("if (cn == \"${clazz.fqname}\") return c$index(c);")
								}
							}
						}
					}
					line("return false;")
				}

				line("static public function internalClassNameToName(internalClassName:String):String") {
					line("var cn = internalClassName;")
					line("switch (cn.length)") {
						for (clazzGroup in program.classes.groupBy { it.name.haxeGeneratedFqName.fqname.length }.toList().sortedBy { it.first }) {
							val length = clazzGroup.first
							val classesWithLength = clazzGroup.second
							line("case $length:")
							indent {
								for (clazz in classesWithLength.sortedBy { it.fqname }) {
									line("if (cn == \"${clazz.name.haxeGeneratedFqName.fqname}\") return \"${clazz.fqname}\";")
								}
							}
						}
					}
					//line("return null;")
					line("throw 'Unknown class \$internalClassName';")
				}

				for (clazz in classes) {
					val index = classToId[clazz]
					line("static private function c$index(c:java_.lang.Class_):Bool") {
						//line("info(c, \"${clazz.name.haxeGeneratedFqName}\", " + (clazz.extending?.fqname?.quote() ?: "null") + ", [" + clazz.implementing.map { "\"${it.fqname}\"" }.joinToString(", ") + "], ${clazz.modifiers}, " + annotations(clazz.runtimeAnnotations) + ");")
						line(annotationsInit(clazz.runtimeAnnotations))
						line("info(c, ${clazz.name.haxeGeneratedFqName}, " + (clazz.extending?.fqname?.quote() ?: "null") + ", [" + clazz.implementing.map { "\"${it.fqname}\"" }.joinToString(", ") + "], ${clazz.modifiers}, " + annotations(clazz.runtimeAnnotations) + ");")
						for ((slot, field) in clazz.fields.withIndex()) {
							val internalName = field.haxeName
							line("field(c, ${internalName.quote()}, $slot, \"${field.name}\", \"${field.descriptor}\", ${field.modifiers}, ${field.genericSignature.quote()}, ${annotations(field.annotations)});");
						}
						for ((slot, method) in clazz.methods.withIndex()) {
							val internalName = method.haxeName
							if (method.name == "<init>") {
								line("constructor(c, ${internalName.quote()}, $slot, ${method.modifiers}, ${method.signature.quote()}, ${method.genericSignature.quote()}, ${annotations(method.annotations)});");
							} else if (method.name == "<clinit>") {
							} else {
								line("method(c, ${internalName.quote()}, $slot, \"${method.name}\", ${method.modifiers}, ${method.desc.quote()}, ${method.genericSignature.quote()}, ${annotations(method.annotations)});");
							}
						}
						line("return true;")
					}
				}
				line("static public function getJavaClass(str:String)") {
					line("return java_.lang.Class_.forName_Ljava_lang_String__Ljava_lang_Class_(HaxeNatives.str(str));")
				}
				line("static private function info(c:java_.lang.Class_, haxeClass:Class<Dynamic>, parent:String, interfaces:Array<String>, modifiers:Int, annotations:Array<Dynamic>)") {
					//line("c._hxClass = Type.resolveClass(internalName);");
					//line("c._internalName = internalName;")
					line("c._hxClass = haxeClass;");
					line("c._internalName = Type.getClassName(haxeClass);")
					line("c._parent = parent;")
					line("c._interfaces = interfaces;")
					line("c._modifiers = modifiers;")
					line("c._fields = [];")
					line("c._methods = [];")
					line("c._constructors = [];")
					line("c._annotations = annotations;")
					line("var initMethod = Reflect.field(haxeClass, '__hx_static__init__'); if (initMethod != null) Reflect.callMethod(haxeClass, initMethod, []);")
				}
				line("static private function field(c:java_.lang.Class_, internalName:String, slot:Int, name:String, type:String, modifiers:Int, genericDescriptor:String, annotations:Array<Dynamic>)") {
					line("var out = new java_.lang.reflect.Field_();")
					line("out.clazz = c;")
					line("out.name = HaxeNatives.str(name);")
					line("out._internalName = name;")
					//line("out.type = getJavaClass(type);")
					line("out.modifiers = modifiers;")
					line("out.signature = HaxeNatives.str(type);")
					line("out.genericSignature = HaxeNatives.str(genericDescriptor);")
					line("out.slot = slot;")
					line("out._annotations = annotations;")
					line("c._fields.push(out);")
				}
				line("static private function method(c:java_.lang.Class_, internalName:String, slot:Int, name:String, modifiers:Int, signature:String, genericDescriptor:String, annotations:Array<Dynamic>)") {
					line("var out = new java_.lang.reflect.Method_();")
					line("out._internalName = internalName;")
					line("out.clazz = c;")
					line("out.name = HaxeNatives.str(name);")
					line("out.signature = HaxeNatives.str(signature);")
					line("out.genericSignature = HaxeNatives.str(genericDescriptor);")
					line("out.slot = slot;")
					line("out.modifiers = modifiers;")
					line("out._annotations = annotations;")
					line("c._methods.push(out);")
				}
				line("static private function constructor(c:java_.lang.Class_, internalName:String, slot:Int, modifiers:Int, signature:String, genericDescriptor:String, annotations:Array<Dynamic>)") {
					line("var out = new java_.lang.reflect.Constructor_();")
					line("out._internalName = internalName;")
					line("out.clazz = c;")
					line("out.slot = slot;")
					line("out.modifiers = modifiers;")
					line("out.signature = HaxeNatives.str(signature);")
					line("out.genericSignature = HaxeNatives.str(genericDescriptor);")
					line("out._annotations = annotations;")
					line("c._constructors.push(out);")
				}
			}
			line(annotationProxyTypes)
		}

		return GenHaxe.ProgramInfo(entryPointClass, entryPointFilePath, vfs)
	}

	fun gen2(stm: AstStm): Indenter {
		this.stm = stm
		val program = program
		val clazz = clazz
		val mutableBody = mutableBody
		return Indenter.gen {
			when (stm) {
				is AstStm.NOP -> Unit
				is AstStm.IF -> {
					line("if (${stm.cond.gen()})") {
						line(stm.strue.gen())
					}
					if (stm.sfalse != null) {
						line("else") { line(stm.sfalse!!.gen()) }
					}
				}
				is AstStm.RETURN -> {
					if (stm.retval != null) {
						line("return ${stm.retval!!.gen()};")
					} else {
						line("return;")
					}
				}
				is AstStm.SET -> {
					val localType = stm.local.type
					val exprType = stm.expr.type
					val adaptor = if (localType != exprType) mappings.getClassAdaptor(exprType, localType) else null
					if (adaptor != null) {
						refs.add(AstType.REF(adaptor.adaptor))
						line("${stm.local.haxeName} = new ${adaptor.adaptor}(${stm.expr.gen()});")
					} else {
						val expr = stm.expr.gen()
						//line("${stm.local.haxeName} = $expr; // ${stm.expr}")
						line("${stm.local.haxeName} = $expr;")
					}
				}
				is AstStm.SET_NEW_WITH_CONSTRUCTOR -> {
					val newClazz = program[stm.target.name]
					//val mapping = mappings.getClassMapping(newClazz)
					refs.add(stm.target)
					val commaArgs = stm.args.map { it.gen() }.joinToString(", ")
					val className = stm.target.haxeTypeNew
					val localHaxeName = stm.local.haxeName

					if (newClazz.nativeName != null) {
						line("$localHaxeName = new $className($commaArgs);")
					} else {
						val methodInline = mappings.getFunctionInline(stm.method)
						if (methodInline != null) {
							line("$localHaxeName = ${methodInline.replacement.replace("@args", commaArgs)};")
						} else {
							line("$localHaxeName = new $className();")
							line("$localHaxeName.${stm.method.haxeName}($commaArgs);")
						}
					}
				}
				is AstStm.SET_ARRAY -> line("${stm.local.haxeName}.set(${stm.index.gen()}, ${stm.expr.gen()});")
				is AstStm.SET_FIELD_STATIC -> {
					refs.add(stm.clazz)
					mutableBody.initClassRef(stm.field.classRef)
					line("${stm.field.haxeStaticText} = ${stm.expr.gen()};")
				}
				is AstStm.SET_FIELD_INSTANCE -> line("${stm.left.gen()}.${stm.field.haxeName} = ${stm.expr.gen()};")
				is AstStm.STM_EXPR -> line("${stm.expr.gen()};")
				is AstStm.STMS -> for (s in stm.stms) line(s.gen())
				is AstStm.STM_LABEL -> line("${stm.label.name}:;")
				is AstStm.BREAK -> line("break;")
				is AstStm.BREAK -> line("break;")
				is AstStm.CONTINUE -> line("continue;")
				is AstStm.WHILE -> {
					line("while (${stm.cond.gen()})") {
						line(stm.iter.gen())
					}
				}
				is AstStm.SWITCH -> {
					line("switch (${stm.subject.gen()})") {
						for (case in stm.cases) {
							val value = case.first
							val caseStm = case.second
							line("case $value:")
							indent {
								line(caseStm.gen())
							}
						}
						line("default:")
						indent {
							line(stm.default.gen())
						}
					}
				}
				is AstStm.TRY_CATCH -> {
					line("try") {
						line(stm.trystm.gen())
					}
					line("catch (__i__exception__: Dynamic)") {
						line("__exception__ = __i__exception__;")
						line(stm.catch.gen())
					}
				}
				is AstStm.THROW -> line("throw ${stm.value.gen()};")
				is AstStm.RETHROW -> line("""HaxeNatives.rethrow(__i__exception__);""")
				is AstStm.MONITOR_ENTER -> line("// MONITOR_ENTER")
				is AstStm.MONITOR_EXIT -> line("// MONITOR_EXIT")
				else -> throw RuntimeException("Unhandled statement $stm")
			}
		}
	}

	fun gen2(body: AstBody): Indenter {
		val method = method
		this.mutableBody = MutableBody(method)

		return Indenter.gen {
			for (local in body.locals) {
				refs.add(local.type)
				line("var ${local.haxeName}: ${local.type.haxeTypeTag} = ${local.type.haxeDefault};")
			}
			if (body.traps.isNotEmpty()) {
				line("var __exception__:Dynamic = null;")
			}
			for (field in method.dependencies.fields2.filter { it.isStatic }) {
				val clazz = field.containingClass
				if (clazz.isInterface) {

				} else {
				}
			}

			val bodyContent = body.stm.gen()

			if (GenHaxe.INIT_MODE == InitMode.LAZY) {
				for (clazzRef in mutableBody.classes) {
					line(names.getHaxeClassStaticInit(clazzRef))
				}
			}
			line(bodyContent)
		}
	}

	fun gen2(e: AstExpr): String {
		return when (e) {
			is AstExpr.THIS -> "this"
			is AstExpr.LITERAL -> names.escapeConstant(e.value)
			is AstExpr.PARAM -> "${e.argument.name}"
			is AstExpr.LOCAL -> "${e.local.haxeName}"
			is AstExpr.UNOP -> "${e.op.symbol}(" + e.right.gen() + ")"
			is AstExpr.BINOP -> {
				val resultType = e.type
				var l = e.left.gen()
				var r = e.right.gen()
				val opSymbol = e.op.symbol

				if (resultType == AstType.INT && e.op.symbol == "/") {
					"Std.int($l / $r)"
				} else {
					when (opSymbol) {
						"lcmp", "cmp", "cmpl", "cmpg" -> "HaxeNatives.$opSymbol($l, $r)"
						else -> {
							val binexpr = "$l $opSymbol $r"
							when (resultType) {
								AstType.INT -> "(($binexpr) | 0)"
								AstType.CHAR -> "(($binexpr) & 0xFFFF)"
								AstType.SHORT -> "((($binexpr) << 16) >> 16)"
								AstType.BYTE -> "((($binexpr) << 24) >> 24)"
								else -> binexpr
							}
						}
					}
				}

			}
			is AstExpr.CALL_BASE -> {
				val method = e.method
				val refMethod = program.get(method) ?: invalidOp("Can't find method: ${method} while generating ${clazz.name}")

				if (e is AstExpr.CALL_STATIC) {
					refs.add(e.clazz)
					mutableBody.initClassRef(e.clazz.classRef)
				}

				val replacement = mappings.getFunctionInline(e.method)
				val commaArgs = e.args.map { it.gen() }.joinToString(", ")

				// Calling a method on an array!!
				if (e is AstExpr.CALL_INSTANCE && e.obj.type is AstType.ARRAY) {
					val args = "${e.obj.gen()}, $commaArgs".trim(',', ' ')
					"HaxeNatives.array${e.method.name.toUcFirst()}($args)"
				} else {
					val base = when (e) {
						is AstExpr.CALL_STATIC -> "${e.clazz.haxeTypeNew}"
						is AstExpr.CALL_SUPER -> "super"
						is AstExpr.CALL_INSTANCE -> "${e.obj.gen()}"
						else -> throw InvalidOperationException("Unexpected")
					}

					if (replacement != null) {
						replacement.replacement.replace("@obj", base).replace("@args", commaArgs)
					} else if (refMethod.getterField != null) {
						if (refMethod.getterField!!.contains('$')) {
							refMethod.getterField!!.replace("\$", base);
						} else {
							"$base.${refMethod.getterField}"
						}

					} else if (refMethod.setterField != null) {
						"$base.${refMethod.setterField} = $commaArgs"
					} else {
						"$base.${method.haxeName}($commaArgs)"
					}
				}
			}
			is AstExpr.INSTANCE_FIELD_ACCESS -> {
				"${e.expr.gen()}.${e.field.haxeName}"
			}
			is AstExpr.STATIC_FIELD_ACCESS -> {
				refs.add(e.clazzName)
				mutableBody.initClassRef(e.field.classRef)

				"${e.field.haxeStaticText}"
			}
			is AstExpr.ARRAY_LENGTH -> "${e.array.gen()}.length"
			is AstExpr.ARRAY_ACCESS -> "${e.array.gen()}.get(${e.index.gen()})"
			is AstExpr.CAST -> {
				refs.add(e.from)
				refs.add(e.to)
				genCast(e.expr.gen(), e.from, e.to)
			}
			is AstExpr.NEW -> {
				refs.add(e.target)
				val className = e.target.haxeTypeNew
				"new $className()"
			}
			is AstExpr.INSTANCE_OF -> {
				refs.add(e.checkType)
				"Std.is(${e.expr.gen()}, ${e.checkType.haxeTypeCast})"
			}
			is AstExpr.NEW_ARRAY -> {
				refs.add(e.type.elementType)
				when (e.counts.size) {
					1 -> {
						if (e.type.elementType !is AstType.Primitive) {
							val desc = e.type.mangle().replace('/', '.') // Internal to normal name!?
							"new HaxeArray(${e.counts[0].gen()}, \"$desc\")"
						} else {
							"new ${e.type.haxeTypeNew}(${e.counts[0].gen()})"
						}
					}
					else -> throw NotImplementedError("Not implemented multidimensional arrays")
				}
			}
			is AstExpr.CLASS_CONSTANT -> "HaxeNatives.resolveClass(${e.classType.mangle().quote()})"
			is AstExpr.CAUGHT_EXCEPTION -> "__exception__"
			is AstExpr.METHOD_CLASS -> {
				val methodInInterfaceRef = e.methodInInterfaceRef
				val methodToConvertRef = e.methodToConvertRef
				val interfaceName = methodInInterfaceRef.classRef.name

				val interfaceLambdaFqname = interfaceName.haxeLambdaName
				"new $interfaceLambdaFqname(" + Indenter.genString {
					//methodInInterfaceRef.type.args

					val argNameTypes = methodInInterfaceRef.type.args.map { it.haxeNameAndType }.joinToString(", ")

					line("function($argNameTypes)") {
						// @TODO: Static + non-static
						//val methodToCallClassName = methodToConvertRef.classRef.name.haxeClassFqName
						//val methodToCallName = methodToConvertRef.haxeName

						val args = methodInInterfaceRef.type.args.map { AstLocal(-1, it.name, it.type) }

						line("return " + gen2(AstExpr.CAST(AstExpr.CALL_STATIC(
							methodToConvertRef.containingClassType,
							methodToConvertRef,
							args.zip(methodToConvertRef.type.args).map { AstExpr.CAST(AstExpr.LOCAL(it.first), it.second.type) }
							), methodInInterfaceRef.type.ret)) + ";"
						)
					}
				} + ")"

			}
			else -> throw NotImplementedError("Unhandled expression $this")
		}
	}

	fun genCast(e:String, from:AstType, to:AstType):String {
		if (from == to) return e

		if (from !is AstType.Primitive && to is AstType.Primitive) {
			return when (from) {
				// @TODO: Check!
				AstType.BOOL.CLASSTYPE -> genCast("($e).booleanValue__Z()", AstType.BOOL, to)
				AstType.BYTE.CLASSTYPE -> genCast("($e).byteValue__B()", AstType.BYTE, to)
				AstType.SHORT.CLASSTYPE -> genCast("($e).shortValue__S()", AstType.SHORT, to)
				AstType.CHAR.CLASSTYPE -> genCast("($e).charValue__C()", AstType.CHAR, to)
				AstType.INT.CLASSTYPE -> genCast("($e).intValue__I()", AstType.INT, to)
				AstType.LONG.CLASSTYPE -> genCast("($e).longValue__J()", AstType.LONG, to)
				AstType.FLOAT.CLASSTYPE -> genCast("($e).floatValue__F()", AstType.FLOAT, to)
				AstType.DOUBLE.CLASSTYPE -> genCast("($e).doubleValue__D()", AstType.DOUBLE, to)
				//AstType.OBJECT -> genCast(genCast(e, from, to.CLASSTYPE), to.CLASSTYPE, to)
				//else -> noImpl("Unhandled conversion $e : $from -> $to")
				else -> genCast(genCast(e, from, to.CLASSTYPE), to.CLASSTYPE, to)
			}
		}

		//if (from is AstType.Primitive && to !is AstType.Primitive) {
		//	// @TODO: Check!
		//	return when (from) {
		//		AstType.BOOL -> genCast("java_.lang.Boolean_.valueOf($e)", AstType.BOOL.CLASSTYPE, to)
		//		AstType.BYTE -> genCast("java_.lang.Byte.valueOf($e)", AstType.BYTE.CLASSTYPE, to)
		//		AstType.SHORT -> genCast("java_.lang.Short.valueOf($e)", AstType.SHORT.CLASSTYPE, to)
		//		AstType.CHAR -> genCast("java_.lang.Character.valueOf($e)", AstType.CHAR.CLASSTYPE, to)
		//		AstType.INT -> genCast("java_.lang.Integer.valueOf($e)", AstType.INT.CLASSTYPE, to)
		//		AstType.LONG -> genCast("java_.lang.Long.valueOf($e)", AstType.LONG.CLASSTYPE, to)
		//		AstType.FLOAT -> genCast("java_.lang.Float.valueOf($e)", AstType.FLOAT.CLASSTYPE, to)
		//		AstType.DOUBLE -> genCast("java_.lang.Double.valueOf($e)", AstType.DOUBLE.CLASSTYPE, to)
		//		else -> noImpl("Unhandled conversion $e : $from -> $to")
		//	}
		//}

		return when (from) {
			is AstType.BOOL -> {
				when (to) {
					is AstType.LONG -> "HaxeNatives.intToLong(($e) ? 1 : 0)"
					is AstType.INT -> "(($e) ? 1 : 0)"
					is AstType.CHAR -> "(($e) ? 1 : 0)"
					is AstType.SHORT -> "(($e) ? 1 : 0)"
					is AstType.BYTE -> "(($e) ? 1 : 0)"
					is AstType.FLOAT, is AstType.DOUBLE -> "(($e) ? 1.0 : 0.0)"
					//else -> genCast("(($e) ? 1 : 0)", AstType.INT, to)
					else -> noImpl("Unhandled conversion $e : $from -> $to")
				}
			}
			is AstType.INT, is AstType.CHAR, is AstType.SHORT, is AstType.BYTE -> {
				when (to) {
					is AstType.LONG -> "HaxeNatives.intToLong($e)"
					is AstType.INT -> "($e)"
					is AstType.BOOL -> "(($e) != 0)"
					is AstType.CHAR -> "(($e) & 0xFFFF)"
					is AstType.SHORT -> "((($e) << 16) >> 16)"
					is AstType.BYTE -> "((($e) << 24) >> 24)"
					is AstType.FLOAT, is AstType.DOUBLE -> "($e)"
					else -> noImpl("Unhandled conversion $from -> $to")
				}
			}
			is AstType.DOUBLE, is AstType.FLOAT -> {
				when (to) {
					is AstType.LONG -> "HaxeNatives.floatToLong($e)"
					is AstType.INT -> "Std.int($e)"
					is AstType.BOOL -> "(Std.int($e) != 0)"
					is AstType.CHAR -> "(Std.int($e) & 0xFFFF)"
					is AstType.SHORT -> "((Std.int($e) << 16) >> 16)"
					is AstType.BYTE -> "((Std.int($e) << 24) >> 24)"
					is AstType.FLOAT, is AstType.DOUBLE -> "($e)"
					else -> noImpl("Unhandled conversion $from -> $to")
				}
			}
			is AstType.LONG -> {
				when (to) {
					is AstType.INT -> "($e).low"
					is AstType.BOOL -> "(($e).low != 0)"
					is AstType.CHAR -> "(($e).low & 0xFFFF)"
					is AstType.SHORT -> "((($e).low << 16) >> 16)"
					is AstType.BYTE -> "((($e).low << 24) >> 24)"
					is AstType.FLOAT, is AstType.DOUBLE -> "HaxeNatives.longToFloat($e)"
					else -> noImpl("Unhandled conversion $from -> $to")
				}
			}
			is AstType.REF, is AstType.ARRAY, is AstType.GENERIC -> {
				when (to) {
					AstType.REF("all.core.AllFunction") -> "(HaxeNatives.getFunction($e))"
					else -> "HaxeNatives.cast2($e, ${to.haxeTypeCast})"
				}
			}
			is AstType.NULL -> "$e"
			else -> noImpl("Unhandled conversion $from -> $to")
		}
	}

	fun gen2(clazz: AstClass): ClassResult {
		this.clazz = clazz

		val isRootObject = clazz.name.fqname == "java.lang.Object"
		val isInterface = clazz.isInterface
		val isAbstract = (clazz.classType == AstClassType.ABSTRACT)
		val isNormalClass = (clazz.classType == AstClassType.CLASS)
		val classType = if (isInterface) "interface" else "class"
		val simpleClassName = clazz.name.haxeGeneratedSimpleClassName
		fun getInterfaceList(keyword: String): String {
			return (
				(if (clazz.implementing.isNotEmpty()) " $keyword " else "") + clazz.implementing.map { it.haxeClassFqName }.joinToString(" $keyword ")
				)
		}
		//val implementingString = getInterfaceList("implements")
		val isInterfaceWithStaticMembers = isInterface && clazz.fields.any { it.isStatic }
		//val isInterfaceWithStaticFields = clazz.name.withSimpleName(clazz.name.simpleName + "\$StaticMembers")
		refs._usedDependencies.clear()

		if (!clazz.extending?.fqname.isNullOrEmpty()) {
			refs.add(AstType.REF(clazz.extending!!))
		}
		for (impl in clazz.implementing) {
			refs.add(AstType.REF(impl))
		}
		//val interfaceClassName = clazz.name.append("_Fields");

		var output = arrayListOf<Pair<FqName, Indenter>>()

		fun writeField(field: AstField, isInterface: Boolean): Indenter = Indenter.gen {
			val static = if (field.isStatic) "static " else ""
			val visibility = if (isInterface) " " else field.visibility.haxe
			val fieldType = field.type
			refs.add(fieldType)
			val defaultValue: Any? = if (field.hasConstantValue) field.constantValue else fieldType.haxeDefault
			val fieldName = field.haxeName
			//if (field.name == "this\$0") println("field: $field : fieldRef: ${field.ref} : $fieldName")
			if (mappings.isFieldAvailable(field.ref) && !field.annotations.contains<HaxeRemoveField>()) {
				val keep = if (field.annotations.contains<JTranscKeep>()) "@:keep " else ""
				line("$keep$static$visibility var $fieldName:${fieldType.haxeTypeTag} = ${names.escapeConstant(defaultValue, fieldType)};")
			}
		}

		fun writeMethod(method: AstMethod, isInterface: Boolean): Indenter {
			this.method = method
			// default methods
			if (isInterface && method.body != null) {
				return Indenter.gen { }
			}
			return Indenter.gen {
				val static = if (method.isStatic) "static " else ""
				val visibility = if (isInterface) " " else method.visibility.haxe
				refs.add(method.methodType)
				val margs = method.methodType.args.map { it.name + ":" + it.type.haxeTypeTag }
				var override = if (method.isOverriding) "override " else ""
				val inline = if (method.isInline) "inline " else ""
				val decl = try {
					"$static $visibility $inline $override function ${method.ref.haxeName}(${margs.joinToString(", ")}):${method.methodType.ret.haxeTypeTag}".trim()
				} catch (e: RuntimeException) {
					println("@TODO abstract interface not referenced: ${method.containingClass.fqname} :: ${method.name} : $e")
					//null
					throw e
				}

				if (isInterface) {
					if (!method.isImplementing) {
						line("$decl;")
					}
				} else {
					val body = mappings.getBody(method.ref) ?: method.annotations[HaxeMethodBody::value]

					if (method.body != null && body == null) {
						line(decl) {
							when (GenHaxe.INIT_MODE) {
								InitMode.START_OLD -> line("__hx_static__init__();")
							}
							line(features.apply(method.body!!, featureSet).gen())
						}
					} else {
						val body2 = body ?: "HaxeNatives.debugger(); throw \"Native or abstract: ${clazz.name}.${method.name} :: ${method.desc}\";"
						line("$decl { $body2 }")
					}
				}
			}
		}

		fun addClassInit(clazz: AstClass) = Indenter.gen {
			when (GenHaxe.INIT_MODE) {
				InitMode.START_OLD, InitMode.LAZY -> line("static public var __hx_static__init__initialized_ = false;");
				else -> Unit
			}
			line("static public function __hx_static__init__()") {
				when (GenHaxe.INIT_MODE) {
					InitMode.START_OLD, InitMode.LAZY -> {
						line("if (__hx_static__init__initialized_) return;")
						line("__hx_static__init__initialized_ = true;")
					}
					else -> Unit
				}
				when (GenHaxe.INIT_MODE) {
					InitMode.START_OLD -> {
						for (clazz in clazz.classDependencies) {
							line(clazz.name.haxeClassFqNameInt + ".__hx_static__init__();")
						}
					}
				}

				if (clazz.hasStaticInit) {
					val methodName = clazz.staticInitMethod!!.haxeName
					line("$methodName();")
				}
			}
		}

		output.add(clazz.name to Indenter.gen {
			line("package ${clazz.name.haxeGeneratedFqPackage};")

			if (isAbstract) line("// ABSTRACT")
			var declaration = "$classType $simpleClassName"
			if (isInterface) {
				if (clazz.implementing.isNotEmpty()) declaration += getInterfaceList("extends")
			} else {
				if (clazz.extending != null && clazz.name.fqname != "java.lang.Object") declaration += " extends ${clazz.extending!!.haxeClassFqName}"
				if (clazz.implementing.isNotEmpty()) declaration += getInterfaceList("implements")
			}

			line(declaration) {
				if (!isInterface) {
					line("public function new()") {
						line(if (isRootObject) "" else "super();")
						if (GenHaxe.INIT_MODE == InitMode.LAZY) {
							line("__hx_static__init__();")
						}
					}
				}
				val nativeImports = mappings.getClassMapping(clazz.ref)?.nativeImports ?: listOf<String>()
				val mappingNativeMembers = (mappings.getClassMapping(clazz.ref)?.nativeMembers ?: listOf<String>())
				val haxeNativeMembers = clazz.annotations[HaxeAddMembers::value]?.toList() ?: listOf()
				val nativeMembers = mappingNativeMembers + haxeNativeMembers

				for (member in nativeMembers) line(member)

				if (!isInterface) {
					for (field in clazz.fields) {
						line(writeField(field, isInterface))
					}
				}

				for (method in clazz.methods) {
					if (isInterface && method.isStatic) continue
					line(writeMethod(method, isInterface))
				}

				if (!isInterface) {
					//println(clazz.fqname + " -> " + program.getAllInterfaces(clazz))
					val isFunctionType = program.isImplementing(clazz, "all.core.AllFunction")

					if (isFunctionType) {
						val executeFirst = clazz.methodsByName["execute"]!!.first()
						line("public const _execute:Function = ${executeFirst.ref.haxeName};")
					}
				}

				/*
				if (isNormalClass) {
					val override = if (isRootObject) " " else "override "
					line("$override public function toString():String { return HaxeNatives.toNativeString(this.toString__Ljava_lang_String_()); }")
				}
				*/
				if (isRootObject) {
					line("public function toString():String { return HaxeNatives.toNativeString(this.toString__Ljava_lang_String_()); }")
					line("public function hashCode():Int { return this.hashCode__I(); }")
				}

				if (!isInterface) {
					line(addClassInit(clazz))
				}
			}

			//if (isInterfaceWithStaticMembers) {
			if (isInterface) {
				line("class ${simpleClassName}_IFields") {
					line("public function new() {}")
					for (field in clazz.fields) line(writeField(field, isInterface = false))

					for (method in clazz.methods.filter { it.isStatic }) line(writeMethod(method, isInterface = false))

					line(addClassInit(clazz))
				}
				val methodsWithoutBody = clazz.methods.filter { it.body == null }
				if (methodsWithoutBody.size == 1 && clazz.implementing.size == 0) {
					// @TODO: Probably it should allow interfaces extending!
					val mainMethod = methodsWithoutBody.first()
					val mainMethodName = mainMethod.ref.haxeName
					val methodType = mainMethod.methodType
					val margs = methodType.args.map { it.name + ":" + it.type.haxeTypeTag }.joinToString(", ")
					val rettype = methodType.ret.haxeTypeTag
					val returnOrEmpty = if (methodType.retVoid) "" else "return "
					val margNames = methodType.args.map { it.name }.joinToString(", ")
					val typeStr = methodType.functionalType
					line("class ${simpleClassName}_Lambda extends java_.lang.Object_ implements ${simpleClassName}") {
						line("private var ___func__:$typeStr;")
						line("public function new(func: $typeStr) { super(); this.___func__ = func; }")
						line("public function $mainMethodName($margs):$rettype { $returnOrEmpty ___func__($margNames); }")
					}
				}
			}
		})

		return ClassResult(output)
	}

	//val FqName.as3Fqname: String get() = this.fqname
	//fun AstMethod.getHaxeMethodName(program: AstProgram): String = this.ref.getHaxeMethodName(program)

	data class ClassResult(val files: List<Pair<FqName, Indenter>>)

	enum class TypeKind { TYPETAG, NEW, CAST }

	val AstVisibility.haxe: String get() = "public"

	val AstType.haxeTypeTag: FqName get() = names.getHaxeType(this, TypeKind.TYPETAG)
	val AstType.haxeTypeNew: FqName get() = names.getHaxeType(this, TypeKind.NEW)
	val AstType.haxeTypeCast: FqName get() = names.getHaxeType(this, TypeKind.CAST)
	val AstType.haxeDefault: Any? get() = names.getHaxeDefault(this)
	val AstType.METHOD_TYPE.functionalType: String get() = names.getHaxeFunctionalType(this)

	val AstLocal.haxeName: String get() = this.name.replace('$', '_')

	val AstField.haxeName: String get() = names.getHaxeFieldName(this)
	val AstFieldRef.haxeName: String get() = names.getHaxeFieldName(this)
	val AstFieldRef.haxeStaticText: String get() = names.getStaticFieldText(this)

	val AstMethod.haxeName: String get() = names.getHaxeMethodName(this)
	val AstMethodRef.haxeName: String get() = names.getHaxeMethodName(this)

	val FqName.haxeLambdaName: String get() = names.getHaxeClassFqNameLambda(this)
	val FqName.haxeClassFqName: String get() = names.getHaxeClassFqName(this)
	val FqName.haxeClassFqNameInt: String get() = names.getHaxeClassFqNameInt(this)
	val FqName.haxeFilePath: String get() = names.getHaxeFilePath(this)
	val FqName.haxeGeneratedFqPackage: String get() = names.getHaxeGeneratedFqPackage(this)
	val FqName.haxeGeneratedFqName: FqName get() = names.getHaxeGeneratedFqName(this)
	val FqName.haxeGeneratedSimpleClassName: String get() = names.getHaxeGeneratedSimpleClassName(this)

	val AstArgument.haxeNameAndType: String get() = this.name + ":" + this.type.haxeTypeTag

	class MutableBody(
		val method: AstMethod
	) {
		val classes = linkedSetOf<AstClassRef>()
		fun initClassRef(classRef: AstClassRef) {
			classes.add(classRef)
		}
	}

	class References {
		var _usedDependencies = hashSetOf<AstType.REF>()
		fun add(type: AstType?) {
			when (type) {
				null -> {
				}
				is AstType.METHOD_TYPE -> {
					for (arg in type.argTypes) add(arg)
					add(type.ret)
				}
				is AstType.REF -> _usedDependencies.add(type)
				is AstType.ARRAY -> add(type.elementType)
				else -> {

				}
			}
		}
	}
}