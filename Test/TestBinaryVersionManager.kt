import java.io.File

fun main() {
    println("准备开始测试 BinaryVersionManager 核心功能...")
    
    // 初始化一个专门用于测试的临时目录
    val testDir = File("./TestFiles")
    val manager = BinaryVersionManager(testDir)

    try {
        // 第一阶段：创建基础全量版本
        println("\n--- 阶段 1: 创建全量基础版本 ---")
        val dataV1 = "这是原始文本：游戏补丁 V1.0。今天天气真不错，这是一个非常好的开局。".toByteArray()
        println("数据 V1 大小：${dataV1.size} 字节")
        manager.createNewArchive(dataV1, "v1.0")
        println("成功创建基础版本 [v1.0]")

        // 第二阶段：导出第一个增量版本
        println("\n--- 阶段 2: 导出增量版本 V2.0 ---")
        val dataV2 = "这是修改后的文本：游戏补丁 V2.0。今天天气真不错，这是一个非常好的开局。增量更新内容：追加了一些新的剧情对话。".toByteArray()
        println("数据 V2 大小：${dataV2.size} 字节")
        manager.exportNewVersion(dataV2, "v2.0")
        
        // 验证文件大小（体现增量效果）
        val patchV2File = File(testDir, "v2.0.patch")
        println("成功导出增量版本 [v2.0]，补丁大小：${patchV2File.length()} 字节")

        // 第三阶段：导出第二个增量版本
        println("\n--- 阶段 3: 导出增量版本 V3.0 ---")
        val dataV3 = "【前缀修改】这是修改后的文本：游戏补丁 V3.0。今天天气真不错，删除了剧情，添加了结束语。".toByteArray()
        println("数据 V3 大小：${dataV3.size} 字节")
        manager.exportNewVersion(dataV3, "v3.0")
        
        val patchV3File = File(testDir, "v3.0.patch")
        println("成功导出增量版本 [v3.0]，补丁大小：${patchV3File.length()} 字节")

        // 第四阶段：清单及导入还原测试
        println("\n--- 阶段 4: 清单验证与多版本还原测试 ---")
        println("当前系统中记录的所有可用版本: ${manager.availableVersions}")

        val restoredV1 = manager.importVersion("v1.0")
        println("\n尝试还原 [v1.0]:")
        println(String(restoredV1))
        println("验证 V1 还原结果: ${dataV1.contentEquals(restoredV1)}")

        val restoredV2 = manager.importVersion("v2.0")
        println("\n尝试还原 [v2.0]:")
        println(String(restoredV2))
        println("验证 V2 还原结果: ${dataV2.contentEquals(restoredV2)}")

        val restoredV3 = manager.importVersion("v3.0")
        println("\n尝试还原 [v3.0]:")
        println(String(restoredV3))
        println("验证 V3 还原结果: ${dataV3.contentEquals(restoredV3)}")

    } catch (e: Exception) {
        println("测试过程中发生异常: ${e.message}")
        e.printStackTrace()
    } finally {
        println("\n--- 阶段 5: 环境清理 ---")
        if (testDir.exists()) {
            val deleted = testDir.deleteRecursively()
            println("临时测试文件夹 ./TestFiles 清理完毕，状态: $deleted")
        }
    }
}
