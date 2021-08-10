package com.example.riderprotector.fragments

import com.example.riderprotector.util.Constants.ZOOM_LEVEL_DEFAULT
import com.example.riderprotector.util.Permissions.hasLocationPermission
import com.example.riderprotector.util.Permissions.requestLocationPermission
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.riderprotector.R
import com.example.riderprotector.addressObject.Coordinate
import com.example.riderprotector.addressObject.Details
import com.example.riderprotector.addressObject.GardaiSatation
import com.example.riderprotector.util.ImgUtil.getBitmapDescriptor
import com.example.riderprotector.util.ImgUtil.px
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import kotlinx.coroutines.launch
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.pow
import kotlin.math.sqrt

class GardaStationMapBlankFragment : Fragment(), OnMapReadyCallback,
    GoogleMap.OnInfoWindowClickListener, GoogleMap.OnInfoWindowLongClickListener, GoogleMap.OnMapLongClickListener,
    EasyPermissions.PermissionCallbacks {

    private lateinit var googleMap: GoogleMap
    private lateinit var supportMapFragment: SupportMapFragment
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val dublin = LatLng(53.3498, -6.2603)
    private var database = FirebaseDatabase.getInstance().getReference("gardai_satation")


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //request location permission, override onRequestPermission result
        if (!hasLocationPermission(requireContext())) {
            requestLocationPermission(this)
        }
        //Initialize View
        val view = inflater.inflate(R.layout.fragment_garda_station_map, container, false)
        //Initialize Map Fragment
        supportMapFragment =
            childFragmentManager.findFragmentById(R.id.garda_station_map) as SupportMapFragment
        //Async Map
        supportMapFragment.getMapAsync(this)
        //FusedLocationProviderClient initialize
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        //return view
        return view
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            SettingsDialog.Builder(requireActivity()).build().show()
        } else {
            requestLocationPermission(this)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        Log.d("location_permission", "permission granted!")
    }


    override fun onMapReady(googleMap: GoogleMap) {
        //assign to google map var
        this.googleMap = googleMap
        //enable location
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission(this)
            return
        }
        googleMap.isMyLocationEnabled = true


        //basic UI settings
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = false // enlarge/shrink button
            isZoomGesturesEnabled = true //double tap to enlarge/shrink
            isScrollGesturesEnabled = true //map scrolling
            isMapToolbarEnabled = true // to google map,
            isCompassEnabled = true //Compass
        }

//        googleMap.addMarker(MarkerOptions().title("testing Marker").position(dublin))

        lifecycleScope.launch {
//            delay(2500)
//            googleMap.animateCamera(
//                CameraUpdateFactory.newLatLngZoom(dublin, ZOOM_LEVEL_DEFAULT),
//                1000,
//                null
//            )
        }
        //default camera position
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dublin, ZOOM_LEVEL_DEFAULT))
        //move to current location
        val currentLocation = getDeviceLocation()
        Log.d("Current_Location_testing", currentLocation.toString())
        addSpotsFromCloud(googleMap)
        googleMap.setOnInfoWindowClickListener(this)
        googleMap.setOnInfoWindowLongClickListener(this)
        googleMap.setOnMapLongClickListener(this)

    }

    private fun moveMap(lat: Double, lng: Double) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val list = mutableListOf<Double>()
                    for (i in snapshot.children) {
                        Log.d(
                            "firebase_gardai_station",
                            "Title:${i.child("details").child("title").value.toString()}"
                        )
                        Log.d(
                            "v",
                            "latitude:${
                                i.child("coordinate").child("latitude").value.toString()
                            }"
                        )
                        var coordinateX =
                            i.child("coordinate").child("latitude").value.toString().toDouble()
                        var coordinateY =
                            i.child("coordinate").child("longitude").value.toString().toDouble()
                        var squareDistanceX = (lat-coordinateX).pow(2)
                        var squareDistanceY = (lng-coordinateY).pow(2)
                        val distance = sqrt(squareDistanceX +squareDistanceY)
                        list.add(distance)
                    }
                    var minValue = list[0]
                    for (i in list){
                        if (i.compareTo(minValue)<0){
                            minValue = i
                        }
                    }
                    for (i in snapshot.children) {
                        var coordinateX =
                            i.child("coordinate").child("latitude").value.toString().toDouble()
                        var coordinateY =
                            i.child("coordinate").child("longitude").value.toString().toDouble()
                        var squareDistanceX = (lat-coordinateX).pow(2)
                        var squareDistanceY = (lng-coordinateY).pow(2)
                        val distance = sqrt(squareDistanceX +squareDistanceY)
                        if (distance == minValue){
                            Log.d("gardai_station_distance",minValue.toString())
                            if (distance>0.1){
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat,lng),
                                        11f
                                    ),
                                    1000,
                                    null
                                )
                            }else if(distance<=0.1 && distance >0.05){
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat,lng),
                                        12f
                                    ),
                                    1000,
                                    null
                                )
                            }else if(distance<=0.05 && distance >0.01){
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat,lng),
                                        13f
                                    ),
                                    1000,
                                    null
                                )
                            }else if(distance<=0.01 && distance >0.005){
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat,lng),
                                        14f
                                    ),
                                    1000,
                                    null
                                )
                            }else{
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(lat,lng),
                                        16f
                                    ),
                                    1000,
                                    null
                                )
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.d("firebase_gardai_station", e.message.toString())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("firebase_garda_station_cancel", error.message)
            }
        })
    }

    private fun getDeviceLocation() {
        try {
            if (hasLocationPermission(context)) {
                val locationRequest = LocationRequest()
                locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                //update times
//                locationRequest.interval = 10000
                //location update times
                locationRequest.numUpdates = 1
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestLocationPermission(this)
                }
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest,
                    object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult?) {
                            //if location result is not null
                            locationResult ?: return
                            Log.d("garda_station_location_testing", locationResult.toString())
                            val lat = locationResult.lastLocation.latitude
                            val lng = locationResult.lastLocation.longitude

                            val deviceLocation = LatLng(lat, lng)
                            moveMap(lat,lng)
                        }

                    }, null
                )

            } else {
                requestLocationPermission(this)
            }
        } catch (e: Exception) {
            Log.d("garda_station_error", e.message.toString())
        }
    }

    private fun addSpotsFromCloud(googleMap: GoogleMap) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    Log.d(
                        "firebase_gardai_station",
                        "Testing:${snapshot.child("0").child("testing").value.toString()}"
                    )
                    for (i in snapshot.children) {
                        Log.d(
                            "firebase_gardai_station",
                            "Title:${i.child("details").child("title").value.toString()}"
                        )
                        Log.d(
                            "firebase_gardai_station",
                            "latitude:${
                                i.child("coordinate").child("latitude").value.toString()
                            }"
                        )
                        googleMap.addMarker(
                            MarkerOptions()
                                .title(i.child("details").child("title").value.toString())
                                .snippet(i.child("details").child("phone").value.toString())
                                .icon(
                                        getBitmapDescriptor(
                                            context!!,
                                            R.drawable.ic_garda_station,
                                            60.px,
                                            60.px
                                        )
                                )
                                .position(
                                    LatLng(
                                        i.child("coordinate")
                                            .child("latitude").value.toString().toDouble(),
                                        i.child("coordinate")
                                            .child("longitude").value.toString().toDouble()
                                    )
                                )
                        )
                    }
                } catch (e: Exception) {
                    Log.d("firebase_gardai_station", e.message.toString())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("firebase_gardai_station", error.message)
            }
        })
    }

    override fun onInfoWindowClick(marker: Marker) {
        var phone = marker.snippet.toString()
        Log.d("firebase_gardai_station", "phone_testing Click: $phone")
        //copy number to phone
        dail(context, phone)
    }

    override fun onInfoWindowLongClick(marker: Marker) {
        val phone = marker.snippet.toString()
        Log.d("firebase_gardai_station", "phone_testing Long Click: $phone")
    }

    private fun dail(context: Context?, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        context?.startActivity(intent)
    }

    override fun onMapLongClick(p0: LatLng) {
        Log.d("onMapLongClick", p0.toString())
        showReportDialog(p0)
    }

    private fun showReportDialog(p0: LatLng) {
        val view = View.inflate(context, R.layout.add_new_address_dialog, null)
        val builder = AlertDialog.Builder(context).setView(view)
        val dialog = builder.create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        val title = view.findViewById<EditText>(R.id.report_title).text
        val address = view.findViewById<EditText>(R.id.report_address).text
        val phone = view.findViewById<EditText>(R.id.report_phone).text

        val reportBTN = view.findViewById<Button>(R.id.btn_report)
        reportBTN.setOnClickListener {
            //test for the value get from user
            Log.d("Report", "Title: $title")
            Log.d("Report", "Brief: $address")
            Log.d("Report", "Brief: $phone")
            uploadNewSpots(title,address,phone,p0, dialog)
            dialog.cancel()
        }
    }

    private fun showReportDialog(title: Editable?, address: Editable?, phone: Editable?, p0: LatLng){
        val view = View.inflate(context, R.layout.add_new_address_dialog, null)
        val builder = AlertDialog.Builder(context).setView(view)
        val dialog = builder.create()
        dialog.show()

        view.findViewById<EditText>(R.id.report_title).text = title
        view.findViewById<EditText>(R.id.report_address).text = address
        view.findViewById<EditText>(R.id.report_phone).text = phone

        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
         val title2 = view.findViewById<EditText>(R.id.report_title).text
         val address2 = view.findViewById<EditText>(R.id.report_address).text
         val phone2 = view.findViewById<EditText>(R.id.report_phone).text

        val reportBTN = view.findViewById<Button>(R.id.btn_report)
        reportBTN.setOnClickListener {
            //test for the value get from user
            Log.d("Report", "Title: $title")
            Log.d("Report", "Brief: $address")
            Log.d("Report", "Brief: $phone")
            uploadNewSpots(title2,address2,phone2,p0, dialog)
            dialog.cancel()
        }
    }


    private fun uploadNewSpots(title: Editable?, address: Editable?, phone: Editable?, p0: LatLng, dialog: AlertDialog) {
        val gardaStation = GardaiSatation(
            Coordinate(p0.latitude,p0.longitude),
            Details(
                address.toString(),
                SimpleDateFormat("dd-MM-yyyy HH:MM:SS").format(Date()),
                phone.toString(),
                title.toString()
            )
        )

        //content validation
        if (title.toString().equals(null)|| title.toString() == ""){
            Log.d("hotspot_upload","title empty!")
            Toast.makeText(context,"Title cannot be empty! Please try Again.", Toast.LENGTH_SHORT).show()
            showReportDialog(title, address, phone, p0)
        }else if(phone.toString().equals(null)|| phone.toString() == ""){
            Log.d("hotspot_upload","phone empty!")
            Toast.makeText(context,"Phone number cannot be empty! Please try Again.", Toast.LENGTH_SHORT).show()
            showReportDialog(title, address, phone, p0)
        }else if(address.toString().equals(null)|| address.toString() == ""){
            Log.d("hotspot_upload","phone number empty!")
            Toast.makeText(context,"Address cannot be empty! Please try Again.", Toast.LENGTH_SHORT).show()
            showReportDialog(title, address, phone, p0)
        }else if(!phone?.let { phoneNumberValid(it) }!!){
            Log.d("hotspot_upload","phone number invalid!")
            Toast.makeText(context,"Phone number you typed is invalid! Please try Again.", Toast.LENGTH_SHORT).show()
            showReportDialog(title, address, phone, p0)
        } else if (!title.toString().equals(null)&& title.toString() != ""
            &&!address.toString().equals(null)&& address.toString() != ""
            &&!phone.toString().equals(null)&& phone.toString() != ""
            &&phoneNumberValid(phone)
        ){
            val key = ("${p0.latitude}+${p0.longitude}").replace('.', '_')
            database.child(key).setValue(gardaStation)
                .addOnSuccessListener {
                    Log.d("firebase_database","new spot added successfully")
                    addNewSpots(googleMap,title,phone,p0)
                    title?.clear()
                    phone?.clear()
                    address?.clear()
                    dialog.cancel()
                }.addOnFailureListener{
                    Log.d("firebase_database","address add failed: $it")
                }
        }
    }

    private fun phoneNumberValid(phone: Editable): Boolean {
        val regExp = "^((13[0-9])|(15[^4])|(18[0-9])|(17[0-8])|(14[5-9])|(166)|(19[8,9])|)\\d{8}$"
        val p = Pattern.compile(regExp)
        val m = p.matcher(phone.toString())
        return m.matches()
    }

    private fun addNewSpots(googleMap: GoogleMap, title: Editable?, phone: Editable?, p0: LatLng) {
        googleMap.addMarker(MarkerOptions()
            .title(title.toString())
            .snippet(phone.toString())
            .icon(
                getBitmapDescriptor(
                    requireContext(),
                    R.drawable.ic_garda_station,
                    60.px,
                    60.px
                )
            )
            .position(p0)
        )
    }

}



