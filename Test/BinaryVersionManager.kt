import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class BinaryVersionManager(val baseDir: File) {

    private val manifestFile = File(baseDir, "manifest.txt")

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /**
     * 获取当前可用的所有版本号列表
     */
    val availableVersions: List<String>
        get() {
            if (!manifestFile.exists()) return emptyList()
            // 读取清单，过滤掉空行
            return manifestFile.readLines().filter { it.isNotBlank() }
        }

    /**
     * 最底层的基础版本号
     */
    val baseVersionId: String?
        get() = availableVersions.firstOrNull()

    /**
     * 创建初始归档（全量保存）
     * @param initialData 首个版本的二进制数据
     * @param versionId 首个版本的标识号
     */
    fun createNewArchive(initialData: ByteArray, versionId: String) {
        if (!baseDir.exists()) baseDir.mkdirs()
        // 清空旧数据文件
        baseDir.listFiles()?.forEach { it.delete() }
        
        // 初始版本记录至清单
        manifestFile.writeText("$versionId\n")
        // 保存全量二进制为 .bin
        File(baseDir, "$versionId.bin").writeBytes(initialData)
    }

    /**
     * 导出新版本（仅保存与上个版本的差异增量）
     * @param newData 新版本的完整数据
     * @param newVersionId 新版本的标识号
     */
    fun exportNewVersion(newData: ByteArray, newVersionId: String) {
        val versions = availableVersions
        if (versions.isEmpty()) {
            // 若尚无任何版本，自动作为基础版创建
            createNewArchive(newData, newVersionId)
            return
        }

        // 导入最新版本进行对比
        val latestVersion = versions.last()
        val oldData = importVersion(latestVersion)

        // 对比并计算增量，写入 patch 文件用于存储
        val patch = computeDelta(oldData, newData)
        File(baseDir, "$newVersionId.patch").writeBytes(patch)
        
        // 版本号追加写入清单
        manifestFile.appendText("$newVersionId\n")
    }

    /**
     * 导入指定版本（通过基准版与增量补丁链式合并出目标版本）
     * @param targetVersionId 目标版本标识号
     */
    fun importVersion(targetVersionId: String): ByteArray {
        val versions = availableVersions
        if (versions.isEmpty()) throw IllegalStateException("没有可用的版本数据。")
        if (!versions.contains(targetVersionId)) throw IllegalArgumentException("未找到待导入的版本：$targetVersionId")

        // 1. 读取首个全量基础版数据
        var currentData = File(baseDir, "${versions[0]}.bin").readBytes()

        // 2. 逐个打上增量补丁，直到推演至目标版本
        for (i in 1 until versions.size) {
            val v = versions[i]
            val patchData = File(baseDir, "$v.patch").readBytes()
            currentData = applyDelta(currentData, patchData)
            
            if (v == targetVersionId) {
                break
            }
        }
        return currentData
    }

    // ==========================================
    // 核心区块：纯手写二进制增量对比与还原算法 
    // ==========================================

    /**
     * 计算两个字节数组的差异，生成极小化的增量补丁包
     */
    private fun computeDelta(oldData: ByteArray, newData: ByteArray): ByteArray {
        val patchStream = ByteArrayOutputStream()
        val dos = DataOutputStream(patchStream)

        var newOffset = 0
        val minMatchSize = 8 // 设定的最小匹配块大小，过滤短碎匹配

        // 哈希索引：以固定步长记录旧数据的特征图块，便于快速定位相似数据
        val oldIndex = HashMap<Int, MutableList<Int>>()
        for (i in 0 until oldData.size - minMatchSize step 4) {
            val hash = oldData.sliceArray(i until i + minMatchSize).contentHashCode()
            oldIndex.getOrPut(hash) { mutableListOf() }.add(i)
        }

        while (newOffset < newData.size) {
            var bestMatchOffset = -1
            var bestMatchLength = 0

            // 尝试在旧数据字典中寻找当前区块位置的匹配项
            if (newOffset <= newData.size - minMatchSize) {
                val currentChunk = newData.sliceArray(newOffset until newOffset + minMatchSize)
                val hash = currentChunk.contentHashCode()
                val possibleOffsets = oldIndex[hash]

                if (possibleOffsets != null) {
                    // 在潜在匹配的起点中，找出能够向后无缝延伸最长的完美匹配
                    for (oldIdx in possibleOffsets) {
                        var matchLen = minMatchSize
                        while (newOffset + matchLen < newData.size && oldIdx + matchLen < oldData.size &&
                            newData[newOffset + matchLen] == oldData[oldIdx + matchLen]
                        ) {
                            matchLen++
                        }
                        if (matchLen > bestMatchLength) {
                            bestMatchLength = matchLen
                            bestMatchOffset = oldIdx
                        }
                    }
                }
            }

            // 【匹配成功】发现冗余数据，执行复用
            if (bestMatchLength >= minMatchSize) {
                dos.writeByte(0) // 补丁指令 0: 数据复用(Copy)
                dos.writeInt(bestMatchOffset) // 指向旧数据的复用起点
                dos.writeInt(bestMatchLength) // 复用长度
                newOffset += bestMatchLength
            } else {
                // 【匹配失败】这段是全新数据
                var addLen = 1
                // 持续扫描，直到遇到下一个满足匹配条件的公共块，再把这一批新数据打包
                while (newOffset + addLen < newData.size) {
                    val nextChunk = if (newOffset + addLen <= newData.size - minMatchSize) {
                        newData.sliceArray(newOffset + addLen until newOffset + addLen + minMatchSize)
                    } else null
                    
                    if (nextChunk != null && oldIndex.containsKey(nextChunk.contentHashCode())) {
                        break
                    }
                    addLen++
                }

                dos.writeByte(1) // 补丁指令 1: 新增自定义数据(Add)
                dos.writeInt(addLen) // 新增实体数据的长度
                dos.write(newData, newOffset, addLen) // 写入实际的新增内容
                newOffset += addLen
            }
        }
        
        dos.writeByte(2) // 补丁指令 2: EOF 文件结束标志
        dos.flush()
        return patchStream.toByteArray()
    }

    /**
     * 基于旧版本数据，打上增量补丁解析并重建出新版本的完整二进制
     */
    private fun applyDelta(baseData: ByteArray, patch: ByteArray): ByteArray {
        val dis = DataInputStream(ByteArrayInputStream(patch))
        val out = ByteArrayOutputStream()

        while (true) {
            val type = try { 
                dis.readByte().toInt() 
            } catch (e: Exception) { 
                break 
            }
            // EOF判定
            if (type == 2) break 

            if (type == 0) {
                // 读取复用指令
                val oldOffset = dis.readInt()
                val length = dis.readInt()
                out.write(baseData, oldOffset, length)
            } else if (type == 1) {
                // 读取新增指令
                val length = dis.readInt()
                val chunk = ByteArray(length)
                dis.readFully(chunk)
                out.write(chunk)
            }
        }
        return out.toByteArray()
    }
}
