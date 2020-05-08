package dev.ghani.twitterkotlinfirebase

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_login.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*


class Login : AppCompatActivity() {
    private var mFirebaseAnalytics: FirebaseAnalytics? = null
    private var mAuth: FirebaseAuth? = null
//    private var mStorageRef: StorageReference? = null
    private var database = FirebaseDatabase.getInstance()
    private var myRef = database.reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mAuth = FirebaseAuth.getInstance();


        setContentView(R.layout.activity_login)

        ivProfile.setOnClickListener(View.OnClickListener {
            checkPermission()
        })

        FirebaseMessaging.getInstance().subscribeToTopic("news")

    }

    fun LoginToFirebase(email:String, password:String){
        mAuth!!.createUserWithEmailAndPassword(email,password)
            .addOnCompleteListener(this){ task ->
                if (task.isSuccessful){
                    Toast.makeText(applicationContext,"Successfull Login",Toast.LENGTH_LONG).show()

                    SaveImageInFirebase()
                } else {
                    Toast.makeText(applicationContext,"Fail Login",Toast.LENGTH_LONG).show()
                }
            }
    }

    @Suppress("DEPRECATION")
    fun SaveImageInFirebase() {
        var currentUser = mAuth!!.currentUser
        val email:String = currentUser!!.email.toString()
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.getReferenceFromUrl("gs://twitter-kotlin-firebase.appspot.com")
        val df = SimpleDateFormat("ddMMyyHHmmss")
        val dataObj = Date()
        val imagePath = SplitString(email) + "." + df.format(dataObj) + ".jpg"
        val imageRef = storageRef.child("images/"+imagePath)

        ivProfile.isDrawingCacheEnabled = true
        ivProfile.buildDrawingCache()

        val drawable = ivProfile.drawable as BitmapDrawable
        val bitmap = drawable.bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)

        uploadTask.addOnFailureListener{
            Toast.makeText(applicationContext, "fail to upload image", Toast.LENGTH_LONG).show()
        }.addOnSuccessListener { taskSnapshot ->
            var DownloadURL = taskSnapshot.storage.downloadUrl.toString()!!
            myRef.child("Users").child(currentUser.uid).child("email").setValue(currentUser.email)
            myRef.child("Users").child(currentUser.uid).child("ProfileImage").setValue(DownloadURL)

            LoadTweets()
        }
    }

    fun LoadTweets() {
        var currentUser = mAuth!!.currentUser

        if(currentUser != null){
            var intent = Intent(this, MainActivity::class.java)
            intent.putExtra("email",currentUser.email)
            intent.putExtra("uid", currentUser.uid)

            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        LoadTweets()
    }

    fun SplitString(email:String):String{
        val split= email.split("@")
        return split[0]
    }


    fun buLoginEvent(view: View){
        //get image from phone
        LoginToFirebase(etEmail.text.toString(), etPassword.text.toString())
    }

    val READIMAGE:Int = 123
    fun checkPermission(){

        if (Build.VERSION.SDK_INT >= 23){
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READIMAGE)
                return
            }
        }
        loadImage()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode){
            READIMAGE->{
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    loadImage()
                }else{
                    Toast.makeText(applicationContext,"Cannot access your images", Toast.LENGTH_LONG).show()
                }
            }
            else-> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }

    }
    val PICK_IMAGE_CODE=123
    fun loadImage() {
        var intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode==PICK_IMAGE_CODE && data !=null && resultCode == RESULT_OK){
            val selectedImage = data.data
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(selectedImage!!, filePathColumn,null,null,null)
            cursor!!.moveToFirst()
            val columnIndex = cursor!!.getColumnIndex(filePathColumn[0])
            val picturePath = cursor!!.getString(columnIndex)
            cursor!!.close()

            ivProfile.setImageBitmap(BitmapFactory.decodeFile(picturePath))
        }
    }
}
