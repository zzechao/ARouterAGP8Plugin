package com.zhouz.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream


/**
 * @author:zhouz
 * @date: 2024/6/20 18:17
 * description：ARouter的收集task
 */
abstract class ARouterCollectTransferTask : DefaultTask() {
    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @Internal
    val jarPaths = mutableSetOf<String>()

    /**
     * 注册class配置组合
     */
    private val registerList = mutableSetOf(
        ScanSetting("IRouteRoot"),
        ScanSetting("IInterceptorGroup"),
        ScanSetting("IProviderGroup"),
    )

    @TaskAction
    fun taskAction() {

        Logger.i("ARouterCollectTransferTask taskAction start")
        val startTime = System.currentTimeMillis()

        val leftSlash: Boolean = File.separator == "/"
        //val pool = ClassPool(ClassPool.getDefault())

        var fileContainsIndex = -1
        val jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(output.get().asFile)))
        jarOutput.use {

            // allJars
            allJars.get().forEachIndexed { index, file ->
                //println("handling " + file.asFile.absolutePath)
                val jarFile = JarFile(file.asFile)
                jarFile.use {
                    it.entries().iterator().forEach { jarEntry ->
                        val entryName = jarEntry.name
                        if (!jarEntry.isDirectory && entryName.isNotEmpty()) {
                            //println("Adding from jar ${jarEntry.name} ${entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME) && shouldProcessPreDexJar(entryName)}")
                            if (entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME) && shouldProcessPreDexJar(entryName)) {
                                //Logger.i("Directory entry name $entryName in arouter pool in jar")
                                jarFile.getInputStream(jarEntry).use {
                                    it.scanClass { Logger.e("\t error jarEntry:${entryName}") }
                                }

                                jarFile.getInputStream(jarEntry).use {
                                    jarOutput.writeEntity(jarEntry.name, it)
                                }
                            } else if (ScanSetting.GENERATE_TO_CLASS_FILE_NAME == entryName) {
                                //Logger.i("Directory CLASS_FILE_NAME entry name $entryName in arouter pool in jar")
                                fileContainsIndex = index
                            } else {
                                jarFile.getInputStream(jarEntry).use {
                                    jarOutput.writeEntity(jarEntry.name, it)
                                }
                            }
                        }
                    }
                }
            }

            // allDirectories
            allDirectories.get().forEach { directory ->
                directory.asFile.walk().forEach { file ->
                    if (file.isFile) {
                        val relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                        val entryName = if (leftSlash) {
                            relativePath
                        } else {
                            relativePath.replace(File.separatorChar, '/')
                        }
                        //println("handling allDirectories entryName $entryName")
                        if (entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME)) {
                            //Logger.i("\tDirectory entry name $entryName in arouter pool")
                            file.inputStream().use {
                                it.scanClass { Logger.e("\t error jarEntry:${entryName}") }
                            }
                            file.inputStream().use {
                                jarOutput.writeEntity(entryName, it)
                            }
                        } else {
                            //println("Adding from directory $entryName")
                            file.inputStream().use {
                                jarOutput.writeEntity(entryName, it)
                            }
                        }
                    }
                }
            }

            // fileContainsFileDo
            if (fileContainsIndex >= 0) {
                allJars.get().getOrNull(fileContainsIndex)?.let {
                    val jarFile = JarFile(it.asFile)
                    jarFile.use {
                        jarFile.getJarEntry(ScanSetting.GENERATE_TO_CLASS_FILE_NAME)?.let {
                            //Logger.i("Insert register code to file ${it.name}")
//                            registerList.forEach { ext ->
//                                if (ext.classList.isEmpty()) {
//                                    Logger.e("No class implements found for interface:" + ext.interfaceName)
//                                } else {
//                                    ext.classList.forEach { Logger.i(it) }
//                                }
//                            }
                            jarFile.getInputStream(it).use {
                                val bytes = it.referHackWhenInit()
                                if (bytes.isNotEmpty()) {
                                    jarOutput.writeEntity(ScanSetting.GENERATE_TO_CLASS_FILE_NAME, bytes)
                                } else {
                                    jarOutput.writeEntity(ScanSetting.GENERATE_TO_CLASS_FILE_NAME, it)
                                }
                            }
                            Logger.i("fileContains write success")
                        }
                    }
                }
            }
        }

        Logger.i("ARouterCollectTransferTask taskAction end durTime:${System.currentTimeMillis() - startTime}")
    }

    // writeEntity methods check if the file has name that already exists in output jar
    private fun JarOutputStream.writeEntity(name: String, inputStream: InputStream) {
        // check for duplication name first
        if (jarPaths.contains(name)) {
            printDuplicatedMessage(name)
        } else {
            putNextEntry(JarEntry(name))
            inputStream.copyTo(this)
            closeEntry()
            jarPaths.add(name)
        }
    }

    private fun JarOutputStream.writeEntity(relativePath: String, byteArray: ByteArray) {
        // check for duplication name first
        if (jarPaths.contains(relativePath)) {
            printDuplicatedMessage(relativePath)
        } else {
            putNextEntry(JarEntry(relativePath))
            write(byteArray)
            closeEntry()
            jarPaths.add(relativePath)
        }
    }


    private fun shouldProcessPreDexJar(entryName: String): Boolean {
        return !entryName.contains("com.android.support") && !entryName.contains("/android/m2repository")
    }

    /**
     * LogisticsCenter注册Router的方法
     */
    private fun InputStream.referHackWhenInit(): ByteArray {
        try {
            val cr = ClassReader(this)
            val cw = ClassWriter(cr, COMPUTE_FRAMES)
            val cv = LogisticsCenterClassVisitor(Opcodes.ASM9, cw)
            cr.accept(cv, ClassReader.EXPAND_FRAMES)
            return cw.toByteArray() ?: ByteArray(0)
        } catch (ex: Throwable) {
            Logger.e("ex:${ex.message}")
        }
        return ByteArray(0)
    }

    /**
     * 扫描class文件
     */
    private fun InputStream.scanClass(error: () -> Unit) {
        try {
            val cr = ClassReader(this)
            val cw = ClassWriter(cr, COMPUTE_FRAMES)
            val cv = ScanClassVisitor(Opcodes.ASM9, cw)
            cr.accept(cv, ClassReader.EXPAND_FRAMES)
        } catch (ex: Throwable) {
            error.invoke()
            Logger.e("ex:${ex.message}")
        }
    }

    private fun printDuplicatedMessage(name: String) =
        Logger.e("Cannot add ${name}, because output Jar already has file with the same name.")

    /**
     * ASM注册一个classVisitor
     */
    inner class LogisticsCenterClassVisitor(api: Int, cv: ClassVisitor) : ClassVisitor(api, cv) {
        override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            var mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (name == ScanSetting.GENERATE_TO_METHOD_NAME) {
                mv = RouteMethodVisitor(Opcodes.ASM9, mv)
            }
            return mv
        }
    }

    /**
     * 注册方法构建Router对象
     */
    inner class RouteMethodVisitor(api: Int, mv: MethodVisitor) : MethodVisitor(api, mv) {
        override fun visitInsn(opcode: Int) {
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
                registerList.forEach {
                    if (it.classList.isEmpty()) {
                        Logger.e("No class implements found for interface:" + it.interfaceName)
                        return@forEach
                    }
                    it.classList.forEach { name ->
                        var entryName = name
                        entryName = entryName.replace('/', '.')
                        Logger.i("visitLdcInsn entryName:$entryName")
                        mv.visitLdcInsn(entryName)//类名
                        // generate invoke register method into LogisticsCenter.loadRouterMap()
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC, ScanSetting.GENERATE_TO_CLASS_NAME, ScanSetting.REGISTER_METHOD_NAME, "(Ljava/lang/String;)V", false
                        )
                    }
                }
            }
            super.visitInsn(opcode)
        }
    }

    /**
     * class扫描
     */
    inner class ScanClassVisitor(api: Int, cv: ClassVisitor) : ClassVisitor(api, cv) {
        override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
            super.visit(version, access, name, signature, superName, interfaces)
            name ?: return
            interfaces ?: return
            registerList.forEach { ext ->
                interfaces.forEach { itName ->
                    if (itName == ext.interfaceName) {
                        ext.classList.add(name)
                    }
                }
            }
        }
    }
}