package com.iebayirli.aicreatecustommodel


import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private val customModelHelper by lazy {
        CustomModelHelper(
            this,
            modelName,
            modelFullName,
            labelName,
            LoadModelFrom.ASSETS_PATH
        )
    }

    private val galleryPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it)
                finish()
        }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            val inputBitmap = MediaStore.Images.Media.getBitmap(
                contentResolver,
                it
            )
            ivImage.setImageBitmap(inputBitmap)

            customModelHelper.exec(inputBitmap, onSuccess = { str ->
                tvResult.text = str
            })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        galleryPermission.launch(readExternalPermission)

        btnRunModel.setOnClickListener {
            getContent.launch(
                "image/*"
            )
        }
    }

    companion object {
        const val readExternalPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
        const val modelName = "flowers"
        const val modelFullName = "flowers" + ".ms"
        const val labelName = "labels.txt"
    }
}
