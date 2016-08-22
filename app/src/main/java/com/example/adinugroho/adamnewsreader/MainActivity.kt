package com.example.adinugroho.adamnewsreader

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.media.AudioManager
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PictureCallback, Camera.ShutterCallback {

    private lateinit var mHolder: SurfaceHolder
    private var mCamera: Camera? = null
    private lateinit var audioMgr: AudioManager
    private var lastVolume: Int = 0
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Fabric.with(this, Crashlytics())

        setContentView(R.layout.activity_main)

        val toolbar = toolbar
        toolbar.title = "Adam News Reader"
        setSupportActionBar(toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermission()
        } else {
            setupPreviewAndCamera()
        }
    }

    fun setupPreviewAndCamera() {
        mHolder = view_surface.holder
        mHolder.addCallback(this)
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        mCamera = Camera.open()

        audioMgr = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        webview.settings.loadWithOverviewMode = true
        webview.settings.useWideViewPort = true
        webview.settings.allowFileAccess = true
        webview.settings.allowContentAccess = true
        webview.loadUrl(sharedPref.getString("url", "http://www.kompas.com/"))

        setFabClickListener()

        fab_view.setOnClickListener {
            if (view_surface.visibility == View.INVISIBLE) {
                view_surface.visibility = View.VISIBLE
                mCamera?.startPreview()
            } else {
                view_surface.visibility = View.INVISIBLE
                mCamera?.stopPreview()
            }
        }
    }

    /// click listener for cam button (set the listener to null to avoid multi capture)
    fun setFabClickListener() {
        fab.setOnClickListener { view ->
            fab.setOnClickListener(null)
//            lastVolume = audioMgr.getStreamVolume(AudioManager.STREAM_SYSTEM)
//            audioMgr.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

            view_surface.visibility = View.VISIBLE
            mCamera?.takePicture(null, null, null, this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_settings) {
            MaterialDialog.Builder(this)
                    .title("URL")
                    .inputType(InputType.TYPE_CLASS_TEXT)
                    .input("URL to show", "http://www.kompas.com", false, { materialDialog, charSequence ->

                    })
                    .positiveText("OK")
                    .onPositive { materialDialog, dialogAction ->
                        val editor = sharedPref.edit()
                        val url = materialDialog.inputEditText?.text?.toString()
                        editor.putString("url", url)
                        editor.apply()

                        webview.loadUrl(url ?: "http://www.kompas.com")
                    }
                    .negativeText("CANCEL")
                    .show()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
        val param = mCamera?.parameters
        val sizes = param?.supportedPreviewSizes
        val selected = sizes!![0]

        val sizeMap = getPictureSize(param?.supportedPictureSizes!!)

        param?.setPreviewSize(selected.width, selected.height)
        param?.setPictureSize(sizeMap["width"]!!, sizeMap["height"]!!)
        param?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

        mCamera?.parameters = param

        mCamera?.setDisplayOrientation(90)
        mCamera?.startPreview()
    }

    fun getPictureSize(supportedPictureSizes: MutableList<Camera.Size>): HashMap<String, Int> {
        var sizes = hashMapOf("width" to 0, "height" to 0)
        var width: Int = 0

        for (size in supportedPictureSizes) {
            Log.v("SIZES:", "${size.width} x ${size.height}")
            if (size.width > width) {
                width = size.width
                sizes = hashMapOf("width" to size.width, "height" to size.height)
            }
        }

        if (width >= 1280) {
            sizes = hashMapOf("width" to 1280, "height" to 720)
        }

        return sizes
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        try {
            mCamera?.setPreviewDisplay(mHolder)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
        Log.i("PREVIEW", "surfaceDestroyed")
    }

    override fun onPause() {
        super.onPause()

        mCamera?.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        mCamera?.release()
    }

    override fun onShutter() {
        Toast.makeText(this, "J.A.N.C.O.K", Toast.LENGTH_SHORT).show()
    }

    /// implementation when pick taken
    override fun onPictureTaken(data: ByteArray?, p1: Camera?) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date())
        val filename = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}${File.separator}${timeStamp}.jpg"
        val file = File(filename)

        try {
            val out = FileOutputStream(file)

            var realImage = BitmapFactory.decodeByteArray(data, 0, data!!.size)
            val exif = ExifInterface(file.toString())

            Log.d("EXIF value", exif.getAttribute(ExifInterface.TAG_ORIENTATION));

            if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("6", true)) {
                realImage = rotate(realImage, 90f)
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("8", true)) {
                realImage = rotate(realImage, 270f)
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("3", true)) {
                realImage = rotate(realImage, 180f)
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equals("0", true)) {
                realImage = rotate(realImage, 90f)
            }

            val bo = realImage.compress(Bitmap.CompressFormat.JPEG, 90, out)

            out.flush()
            out.fd.sync()
            out.close()

            Toast.makeText(this, "J.A.N.C.O.K", Toast.LENGTH_SHORT).show()

            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, MediaScannerConnection.OnScanCompletedListener { s, uri ->
                Log.v("MAIN", "Scan Completed")
            })

            setFabClickListener()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mCamera?.startPreview()
    }

    fun rotate(bitmap: Bitmap, degree: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        val mtx = Matrix()
        mtx.setRotate(degree)

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true)
    }

    /// only request permission (require) for android M and up
    fun requestPermission() {
        val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.INTERNET)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1)
            } else {
                setupPreviewAndCamera()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1) {
            if (grantResults.size > 0) {
                setupPreviewAndCamera()
            }
        }
    }
}
