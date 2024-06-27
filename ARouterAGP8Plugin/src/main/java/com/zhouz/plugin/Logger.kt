package com.zhouz.plugin

import org.gradle.api.Project


/**
 * @author:zhouz
 * @date: 2024/6/26 17:05
 * description：日志输出
 */
object Logger {
    private lateinit var logger: org.gradle.api.logging.Logger

    fun make(project: Project) {
        logger = project.logger
    }

    fun i(info: String) {
        println("ARouter::Register >>> $info")
    }

    fun e(error: String) {
        logger.error("ARouter::Register >>> $error")
    }

    fun w(warning: String) {
        logger.warn("ARouter::Register >>> $warning")
    }
}