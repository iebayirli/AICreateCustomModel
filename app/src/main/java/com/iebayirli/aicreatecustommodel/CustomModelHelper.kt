package com.iebayirli.aicreatecustommodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import com.huawei.hms.mlsdk.common.MLException
import com.huawei.hms.mlsdk.custom.*
import java.io.IOException
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CustomModelHelper(
    private val mContext: Context,
    modelName: String,
    modelFullName: String,
    labelName: String,
    private val loadModelFrom: LoadModelFrom
) {

    private var mModelName: String = modelName
    private var mModelFullName: String = modelFullName
    private var mLabelName: String = labelName

    private var labelList: ArrayList<String> = ArrayList()

    private var OUTPUT_SIZE : Int? = null

    init {
        getLabels()
    }

    fun exec(bitmap: Bitmap, onSuccess: (String) -> Unit) {
        val localModel: MLCustomLocalModel = when (loadModelFrom) {
            LoadModelFrom.ASSETS_PATH -> {
                MLCustomLocalModel.Factory(mModelName)
                    .setAssetPathFile(mModelFullName)
                    .create()
            }
            LoadModelFrom.LOCAL_FULL_PATH -> {
                MLCustomLocalModel.Factory(mModelName)
                    .setLocalFullPathFile(mModelFullName)
                    .create()
            }
        }
        val settings = MLModelExecutorSettings.Factory(localModel).create()

        try {
            val mMLModelExecutor = MLModelExecutor.getInstance(settings)
            val impl = execImpl(bitmap)

            impl?.let {
                mMLModelExecutor.exec(it.first, it.second).addOnSuccessListener { mModelOperator ->
                    val output: Array<FloatArray> = mModelOperator.getOutput(0)
                    val probabilities = output[0]

                    onSuccess.invoke(processResult(probabilities))

                    Toast.makeText(mContext, "Success", Toast.LENGTH_SHORT).show()

                }.addOnFailureListener { e ->
                    val error = e as MLException
                    Log.e(TAG, "interpret failed, because ${error.message} ${error.errCode}");

                }.addOnCompleteListener {
                    try {
                        mMLModelExecutor!!.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: MLException) {
            Log.e(TAG, "Message: ${e.message}, Code: ${e.errCode}")
        }

    }

    private fun execImpl(bitmap: Bitmap): Pair<MLModelInputs, MLModelInputOutputSettings>? {
        val inputBitmap = Bitmap.createScaledBitmap(
            bitmap,
            BITMAP_WIDTH,
            BITMAP_HEIGHT, true
        )
        val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        for (i in 0..223) {
            for (j in 0..223) {
                val pixel = inputBitmap.getPixel(i, j)
                input[batchNum][j][i][0] = (Color.red(pixel) - 128.0f) / 128.0f
                input[batchNum][j][i][1] = (Color.green(pixel) - 128.0f) / 128.0f
                input[batchNum][j][i][2] = (Color.blue(pixel) - 128.0f) / 128.0f
            }
        }
        var mlModelInputs: MLModelInputs? = null
        try {
            mlModelInputs = MLModelInputs.Factory().add(input).create()
        } catch (e: MLException) {
            Log.e(TAG, "add inputs failed! ${e.message} ")
        }

        var mlModelInputOutputSettings: MLModelInputOutputSettings? = null
        try {
            mlModelInputOutputSettings = MLModelInputOutputSettings.Factory()
                .setInputFormat(0, MLModelDataType.FLOAT32, intArrayOf(1, 224, 224, 3))
                .setOutputFormat(
                    0, MLModelDataType.FLOAT32, intArrayOf(
                        1,
                        OUTPUT_SIZE!!
                    )
                )
                .create()
        } catch (e: MLException) {
            Log.e(TAG, "set input output format failed! ${e.message} ")
        }

        Pair(
            mlModelInputs,
            mlModelInputOutputSettings
        ).letCheckNull { mlModelInputs, mlModelInputOutputSettings ->
            return Pair(mlModelInputs, mlModelInputOutputSettings)
        }
        return null
    }


    private fun processResult(probabilities: FloatArray): String {
        val localResult: HashMap<String, Float> = HashMap()
        for (i in probabilities.indices) {
            localResult[labelList[i]] = probabilities[i]
        }
        val result: TreeMap<String, Float> =
            TreeMap()
        result.putAll(localResult)

        val builder = StringBuilder()

        var total = 0
        val df = DecimalFormat("0.00%")
        for ((key, value) in result) {
            if (total == PRINT_LENGTH || value <= 0) {
                break
            }
            builder.append("No ")
                .append(total)
                .append("：")
                .append(key)
                .append(" / ")
                .append(df.format(value))
                .append(System.lineSeparator())
            total++
        }
        return builder.toString()
    }

    private fun getLabels() {
        try {
            val labels = mContext.assets.open(mLabelName).bufferedReader().use {
                it.readLines()
            }
            labelList.addAll(labels)
            OUTPUT_SIZE = labelList.size
        } catch (e: IOException) {
            Log.e(TAG, "Asset file doesn't exist: ${e.message}")
        }
    }

    companion object {
        const val batchNum = 0
        const val BITMAP_WIDTH = 224 // 128, 224
        const val BITMAP_HEIGHT = 224 // 128, 224
        const val PRINT_LENGTH = 10
        const val TAG = "CustomModelHelper"
    }
}

enum class LoadModelFrom {
    ASSETS_PATH,
    LOCAL_FULL_PATH
}

inline fun <A, B, R> Pair<A?, B?>.letCheckNull(block: (A, B) -> R): R? =
    when (null) {
        first, second -> null
        else -> block(first as A, second as B)
    }
