package com.zhouz.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register


/**
 * @author:zhouz
 * @date: 2024/6/20 17:44
 * description：ARouter 针对 agp 8.0 的 插件
 */
class ARouterAGP8Plugin : Plugin<Project> {
    override fun apply(project: Project) {
        Logger.make(project)
        project.plugins.withType(AppPlugin::class.java) {
            val androidComponents =
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val task = project.tasks.register<ARouterCollectTransferTask>("${variant.name}RouterCollectTask")
                variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                    .use(task)
                    .toTransform(
                        ScopedArtifact.CLASSES,
                        ARouterCollectTransferTask::allJars,
                        ARouterCollectTransferTask::allDirectories,
                        ARouterCollectTransferTask::output
                    )
            }
        }
    }
}