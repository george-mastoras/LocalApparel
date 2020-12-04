package cmsc436.semesterproject.localapparel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.Location.distanceBetween
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class FeedActivity : AppCompatActivity() {

    lateinit var mNavBar: BottomNavigationView
    private lateinit var databaseListings: DatabaseReference
    private lateinit var storageListings: StorageReference
    lateinit var listingsRecyclerView: RecyclerView
    lateinit var distancesSpinner: Spinner
    lateinit var listings: MutableList<ApparelItem>

    // Location Variables
    var mDistance: Float = 5f
    private lateinit var locationManager: LocationManager
    private lateinit var mLocationListener: LocationListener
    private var mLastLocationReading: Location? = null
    private val mMinTime: Long = 5000
    private val mMinDistance = 1000.0f

    // CITATION: based upon Lab7
    var databaseRefreshListingsListener: ValueEventListener = object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            listings.clear()

            var item: ApparelItem? = null
            for (postSnapshot in dataSnapshot.children) {
                try {
                    item = postSnapshot.getValue(ApparelItem::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                    return
                } finally {
                    var itemLat = item!!.itemLatitude as Double
                    var itemLong = item.itemLongitude as Double
                    var diff = FloatArray(1)
                    distanceBetween(
                        itemLat,
                        itemLong,
                        mLastLocationReading!!.latitude,
                        mLastLocationReading!!.longitude,
                        diff
                    )

                    var dist = diff[0] * 0.000621371192;


                    val c = Calendar.getInstance()
                    var today = dateString(
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                        c.get(Calendar.DAY_OF_MONTH)
                    )
                    var listingExpirationDate = item.listingExpirationDate
                    // Ensure that the specified expiration date has not already expired
                    val sdf = SimpleDateFormat("yyyy-MM-dd")
                    val expirationDate: Date = sdf.parse(listingExpirationDate)
                    val todayDate: Date = sdf.parse(today)

                    if ((dist <= mDistance) && !todayDate.after(expirationDate)) {
                        listings.add(item!!)
                    }
                }
            }

            listingsRecyclerView.adapter = ItemRecyclerViewAdapter(
                this@FeedActivity,
                listings,
                R.layout.apparel_item
            )
        }

        override fun onCancelled(databaseError: DatabaseError) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)

        databaseListings = FirebaseDatabase.getInstance().getReference("listings")
        storageListings = FirebaseStorage.getInstance().getReference("listings")

        mLocationListener = makeLocationListener()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        listings = ArrayList()
        listingsRecyclerView = findViewById<View>(R.id.feed) as RecyclerView
        listingsRecyclerView.layoutManager = LinearLayoutManager(this)


        var dividerItemDecoration = DividerItemDecoration(
            applicationContext,
            LinearLayoutManager.VERTICAL
        )
        dividerItemDecoration.setDrawable(
            applicationContext.resources.getDrawable(R.drawable.recycler_view_line)
        )
        listingsRecyclerView.addItemDecoration(dividerItemDecoration)

        distancesSpinner = findViewById<View>(R.id.distances_spinner) as Spinner

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            this,
            R.array.distances_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            distancesSpinner.adapter = adapter
        }

        distancesSpinner.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, pos: Int, id: Long
            ) {
                mDistance = parent.getItemAtPosition(pos).toString().split(" ")[0].toFloat()
                databaseListings.addListenerForSingleValueEvent(databaseRefreshListingsListener)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        })

        mNavBar = findViewById<View>(R.id.bottom_navigation) as BottomNavigationView
        mNavBar.setOnNavigationItemSelectedListener { item ->
            when(item.itemId) {
                R.id.browse -> {
                    // Respond to navigation item 1 click
                    true
                }
                R.id.add -> {
                    // Respond to navigation item 2 click
                    val intent = Intent(this, AddItemActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.listings -> {
                    // Respond to navigation item 3 click
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()

        databaseListings.addValueEventListener(databaseRefreshListingsListener)
    }

    // CITATION: Lab11
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                "android.permission.ACCESS_FINE_LOCATION"
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                applicationContext,
                "android.permission.ACCESS_COARSE_LOCATION"
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this@FeedActivity, arrayOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
                ),
                MY_PERMISSIONS_LOCATION
            )
        } else getLocationUpdates()
    }

    // CITATION: Lab11
    private fun getLocationUpdates(){
        try {
            var loc = locationManager!!.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null && (System.currentTimeMillis() - loc.time) < FIVE_MINS) {
                mLastLocationReading = loc;
            }

            loc = locationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null && (System.currentTimeMillis() - loc.time) < FIVE_MINS) {
                mLastLocationReading = loc;
            }

            if (null != locationManager!!.getProvider(LocationManager.NETWORK_PROVIDER)) {
                Log.i(TAG, "Network location updates requested")
                locationManager!!.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    mMinTime,
                    mMinDistance,
                    mLocationListener
                )
            }

            if (null != locationManager!!.getProvider(LocationManager.GPS_PROVIDER)) {
                Log.i(TAG, "GPS location updates requested")
                locationManager!!.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    mMinTime,
                    mMinDistance,
                    mLocationListener
                )
            }

        } catch (e: SecurityException) {
            Log.d(TAG, e.localizedMessage)
        }
    }

    private fun makeLocationListener(): LocationListener {
        return object : LocationListener {
            // CITATION: Lab11
            override fun onLocationChanged(location: Location) {
                if(mLastLocationReading == null) {
                    mLastLocationReading = location
                }
                else if(mLastLocationReading!!.time < location.time) {
                    mLastLocationReading = location
                }
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }
    }

    // CITATION: Lab11
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_LOCATION -> {
                var g = 0
                Log.d(TAG, "Perm?: " + permissions.size + " -? " + grantResults.size)
                for (perm in permissions) Log.d(TAG, "Perm: " + perm + " --> " + grantResults[g++])
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) getLocationUpdates() else {
                    Log.i(TAG, "Permission was not granted to access location")
                    finish()
                }
            }
        }
    }

    companion object {
        private val TAG = "LocalApparel-FeedActivity"
        const val MY_PERMISSIONS_LOCATION = 4
        val ONE_DAY = 86400000
        private const val FIVE_MINS = 5 * 60 * 1000.toLong()
        // CITATION: Lab4
        private fun dateString(year: Int, monthOfYear: Int, dayOfMonth: Int): String {
            var month = monthOfYear

            // Increment monthOfYear for Calendar/Date -> Time Format setting
            month++
            var mon = "" + month
            var day = "" + dayOfMonth

            if (month < 10)
                mon = "0$month"
            if (dayOfMonth < 10)
                day = "0$dayOfMonth"

            return "$year-$mon-$day"
        }
    }
}