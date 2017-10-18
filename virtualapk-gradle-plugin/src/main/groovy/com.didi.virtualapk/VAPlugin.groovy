package com.didi.virtualapk

import com.android.build.gradle.api.ApplicationVariant
import com.didi.virtualapk.hooker.TaskHookerManager
import com.didi.virtualapk.transform.StripClassAndResTransform


import com.didi.virtualapk.utils.FileBinaryCategory
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

/**
 * VirtualAPK gradle plugin for plugin project
 *
 * @author zhengtao
 */

class VAPlugin extends BasePlugin {

    //Files be retained after host apk build
    //private def hostFileNames = ['versions', 'R.txt', 'mapping.txt', 'versions.txt', 'Host_R.txt'] as Set

    /**
     * Stores files generated by the host side and is used when building plugin apk
     */
    private def hostDir

    /**
     * TaskHooker manager, registers hookers when apply invoked
     */
    private TaskHookerManager taskHookerManager

    @Inject
    public VAPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry)
    }

    @Override
    void apply(final Project project) {
        super.apply(project)

        if (!isBuildingPlugin) {
            return
        }

        hostDir = new File(project.rootDir, "host")
        if (!hostDir.exists()) {
            hostDir.mkdirs()
        }

        project.android.registerTransform(new StripClassAndResTransform(project))

        taskHookerManager = new TaskHookerManager(project, instantiator)
        taskHookerManager.registerTaskHookers()


        project.afterEvaluate {
            project.android.applicationVariants.each { ApplicationVariant variant ->

                checkConfig()

                virtualApk.with {
                    packageName = variant.applicationId
                    packagePath = packageName.replaceAll('\\.', File.separator)
                    hostSymbolFile = new File(hostDir, "Host_R.txt")
                    hostDependenceFile = new File(hostDir, "versions.txt")
                }
            }
        }
    }

    /**
     * Check the plugin apk related config infos
     */
    private void checkConfig() {
        int packageId = virtualApk.packageId
        if (packageId == 0) {
            def err = new StringBuilder('you should set the packageId in build.gradle,\n ')
            err.append('please declare it in application project build.gradle:\n')
            err.append('    virtualApk {\n')
            err.append('        packageId = 0xXX \n')
            err.append('    }\n')
            err.append('apply for the value of packageId, please contact with zhengtao@didichuxing.com\n')
            throw new InvalidUserDataException(err.toString())
        }

        String targetHost = virtualApk.targetHost
        if (!targetHost) {
            def err = new StringBuilder('\nyou should specify the targetHost in build.gradle, e.g.: \n')
            err.append('    virtualApk {\n')
            err.append('        //when target Host in local machine, value is host application directory\n')
            err.append('        targetHost = ../xxxProject/app \n')
            err.append('    }\n')
            throw new InvalidUserDataException(err.toString())
        }

        File hostLocalDir = new File(targetHost)
        if (!hostLocalDir.exists()) {
            def err = "The directory of host application doesn't exist! Dir: ${hostLocalDir.absoluteFile}"
            throw new InvalidUserDataException(err)
        }

        File hostR = new File(hostLocalDir, "build/VAHost/Host_R.txt")
        if (hostR.exists()) {
            def dst = new File(hostDir, "Host_R.txt")
            use(FileBinaryCategory) {
                dst << hostR
            }
        } else {
            def err = new StringBuilder("Can't find ${hostR.path}, please check up your host application\n")
            err.append("  need apply com.didi.virtualapk.host in build.gradle of host application\n ")
            throw new InvalidUserDataException(err.toString())
        }

        File hostVersions = new File(hostLocalDir, "build/VAHost/versions.txt")
        if (hostVersions.exists()) {
            def dst = new File(hostDir, "versions.txt")
            use(FileBinaryCategory) {
                dst << hostVersions
            }
        } else {
            def err = new StringBuilder("Can't find ${hostVersions.path}, please check up your host application\n")
            err.append("  need apply com.didi.virtualapk.host in build.gradle of host application \n")
            throw new InvalidUserDataException(err.toString())
        }

        File hostMapping = new File(hostLocalDir, "build/VAHost/mapping.txt")
        if (hostMapping.exists()) {
            def dst = new File(hostDir, "mapping.txt")
            use(FileBinaryCategory) {
                dst << hostMapping
            }
        }
    }
}
