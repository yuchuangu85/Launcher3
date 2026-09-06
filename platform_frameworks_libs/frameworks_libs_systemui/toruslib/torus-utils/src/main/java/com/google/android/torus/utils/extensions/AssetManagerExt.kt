/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.torus.utils.extensions

import android.content.res.AssetManager
import java.nio.ByteBuffer
import java.nio.channels.Channels

/**
 * Extends [AssetManager] to read uncompressed assets.
 *
 * @param assetPathAndName The string of the asset path and name inside the assets folder.
 *        The asset must be uncompressed.
 *
 * @return A [ByteBuffer] containing the asset.
 */
fun AssetManager.readUncompressedAsset(assetPathAndName: String): ByteBuffer {

    openFd(assetPathAndName).use { fd ->
        val input = fd.createInputStream()
        val dst = ByteBuffer.allocate(fd.length.toInt())

        val src = Channels.newChannel(input)
        src.read(dst)
        src.close()

        return dst.apply { rewind() }
    }
}

/**
 * Extends [AssetManager] to read assets.
 *
 * @param assetPathAndName The string of the asset path and name inside the assets folder.
 * @return A [ByteBuffer] containing the asset.
 */
fun AssetManager.readAsset(assetPathAndName: String): ByteBuffer {
    open(assetPathAndName).use { inputStream ->
        val byteArray = inputStream.readBytes()

        val dst = ByteBuffer.allocate(byteArray.size)
        dst.put(byteArray)
        inputStream.close()

        return dst.apply { rewind() }
    }
}

/**
 * Extends [AssetManager] to read an asset as a [String].
 *
 * @param assetName The string of the asset inside the assets folder.
 * @return A [String] containing the asset information.
 */
fun AssetManager.readAssetAsString(assetName: String): String {
    return String(readAsset(assetName).array(), Charsets.ISO_8859_1)
}
