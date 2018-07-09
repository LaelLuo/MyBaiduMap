package io.github.laelluo.mybaidumap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.ArraySet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.*
import com.baidu.mapapi.search.poi.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var client: LocationClient
    private lateinit var baiduMap: BaiduMap
    private lateinit var poiSearch: PoiSearch
    private var currentMode: MyLocationConfiguration.LocationMode = MyLocationConfiguration.LocationMode.NORMAL
    private var currentType: Int = BaiduMap.MAP_TYPE_NORMAL
    private var locationCount = 0
    private var requestCount = 0
    private var accuracy = 0F
    private var direction = 0F
    private var lastX = 0F
    private var latitude = 0.00
    private var longitude = 0.00
    private var city = ""
    private val histories = ArraySet<String>()
    private val markers = ArraySet<Overlay>()
    private lateinit var poiResult: PoiResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SDKInitializer.initialize(applicationContext)
        setContentView(R.layout.activity_main)
        initHistories()
        initPermission()
        initSensor()
        initView()
        initClient()
        initSearch()
    }

    private fun initHistories() = getHistories().forEach { histories.add(it) }

    private fun getHistories() = getData().getStringSet("histories", setOf())

    private fun saveHistories() {
        val stringBuilder = StringBuilder()
        histories.forEach { stringBuilder.append("$it ") }
        setData { putStringSet("histories", histories) }
    }

    private fun initSearch() {
        poiSearch = PoiSearch.newInstance().apply {
            setOnGetPoiSearchResultListener(object : OnGetPoiSearchResultListener {
                override fun onGetPoiIndoorResult(p0: PoiIndoorResult?) {}
                override fun onGetPoiDetailResult(p0: PoiDetailResult?) {}
                override fun onGetPoiResult(p0: PoiResult?) {
                    p0?.let {
                        if (it.error == SearchResult.ERRORNO.NO_ERROR) {
                            poiResult = it
                            baiduMap.clear()
                            markers.clear()
                            it.allPoi.forEach {
                                Log.e("Poi", "${it.name} ${it.address}")
                                markers.add(baiduMap.addOverlay(MarkerOptions()
                                        .position(it.location)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
                                        .extraInfo(Bundle().apply { putInt("index", poiResult.allPoi.indexOf(it)) })))
                                val builder = LatLngBounds.Builder()
                                markers.forEach { marker -> if (marker is Marker) builder.include(marker.position) }
                                baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngBounds(builder.build()))
                            }
                            baiduMap.setOnMarkerClickListener {
                                val poiInfo = poiResult.allPoi[it.extraInfo.getInt("index")]
                                baiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().apply {
                                    target(poiInfo.location)
                                    zoom(18F)
                                }.build()))
                                val view = newInfoView(poiInfo.name, poiInfo.address) {
                                    startActivity(Intent(this@MainActivity, RouteActivity::class.java).apply {
                                        putExtra("from", doubleArrayOf(latitude, longitude))
                                        putExtra("to", doubleArrayOf(poiInfo.location.latitude, poiInfo.location.longitude))
                                        putExtra("name", poiInfo.name)
                                    })
                                }
                                baiduMap.showInfoWindow(InfoWindow(view, poiInfo.location, -80))
                                true
                            }
                        } else {
                            location_floatingactionbutton_main.snack("未找到结果")
                        }
                    }
                }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun initClient() {
        client = LocationClient(this).apply {
            registerLocationListener { location ->
                latitude = location.latitude
                longitude = location.longitude
                accuracy = location.radius
                baiduMap.setMyLocationData(MyLocationData.Builder().apply {
                    accuracy(accuracy)
                    direction(direction)
                    latitude(latitude)
                    longitude(longitude)
                }.build())
                if (locationCount < 2) {
                    locationCount++
                    baiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().apply {
                        target(LatLng(latitude, longitude))
                        zoom(18F)
                    }.build()))
                }
                GeoCoder.newInstance().apply {
                    setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                        override fun onGetGeoCodeResult(p0: GeoCodeResult?) {}
                        override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                            p0?.let {
                                if (it.error == SearchResult.ERRORNO.NO_ERROR) {
                                    city = it.addressDetail.city
                                }
                            }
                        }
                    })
                    reverseGeoCode(ReverseGeoCodeOption().location(LatLng(latitude, longitude)).newVersion(1))
                    destroy()
                }
            }
        }
        client.locOption = LocationClientOption().apply {
            openGps = true
            setCoorType("bd09ll")
            setScanSpan(1000)
        }
        client.start()
    }

    private fun initView() {
        search_floatingactionbutton_main.setOnClickListener {
            if (it.visibility != View.GONE) {
                search_searchview_main.open()
                it.visibility = View.GONE
            }
        }
        search_searchview_main.apply {
            setNewHistoryList(histories.toList())
            setOnSearchBackIconClickListener {
                close()
                search_floatingactionbutton_main.visibility = View.VISIBLE
            }
            setOnSearchActionListener {
                val key = it.trim()
                if (key.isEmpty()) {
                    editTextView.text.clear()
                    snack("搜索不能为空")
                } else {
                    histories.add(key)
                    saveHistories()
                    setNewHistoryList(histories.toList())
                    poiSearch.searchInCity(PoiCitySearchOption().city(city).keyword(key))
                    close()
                    search_floatingactionbutton_main.visibility = View.VISIBLE
                }
            }
            setHistoryItemClickListener { key, _ ->
                poiSearch.searchInCity(PoiCitySearchOption().city(city).keyword(key))
                close()
                search_floatingactionbutton_main.visibility = View.VISIBLE
            }
            setOnCleanHistoryClickListener {
                histories.clear()
                saveHistories()
            }
        }
        map_mapview_main.apply {
            baiduMap = map
            showZoomControls(false)
            showScaleControl(false)
            getChildAt(1)?.visibility = View.GONE
        }
        baiduMap.apply {
            setMyLocationConfiguration(MyLocationConfiguration(currentMode, true, null))
            isMyLocationEnabled = true
            setCompassEnable(false)
            uiSettings.isOverlookingGesturesEnabled = false
//            animateMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().apply {
//                overlook(0F)
//            }.build()))
            setOnMapLongClickListener { location ->
                baiduMap.apply {
                    animateMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().apply {
                        target(location)
                        zoom(18F)
                    }.build()))
                    GeoCoder.newInstance().apply {
                        setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                            override fun onGetGeoCodeResult(p0: GeoCodeResult?) {}
                            override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                                p0?.let {
                                    if (it.error == SearchResult.ERRORNO.NO_ERROR) {
                                        markers.forEach { it.remove() }
                                        markers.add(baiduMap.addOverlay(MarkerOptions().position(location)
                                                .icon(BitmapDescriptorFactory.fromResource(io.github.laelluo.mybaidumap.R.drawable.ic_marker))))
                                        showInfoWindow(InfoWindow(newInfoView("选定的点", it.address) {
                                            startActivity(Intent(this@MainActivity, RouteActivity::class.java).apply {
                                                putExtra("from", doubleArrayOf(latitude, longitude))
                                                putExtra("to", doubleArrayOf(location.latitude, location.longitude))
                                                putExtra("name", "选定的点")
                                            })
                                        }, location, -80))
                                    }
                                }
                            }
                        })
                        reverseGeoCode(ReverseGeoCodeOption().location(location).newVersion(1))
                        destroy()
                    }
                }
            }
        }
        location_floatingactionbutton_main.apply {
            setOnClickListener { changeLocationMode() }
            setOnLongClickListener { changeMapType() }
        }
    }

    private fun newInfoView(name: String, address: String, function: (View) -> Unit) = layoutInflater.inflate(R.layout.view_info_main, map_mapview_main, false).apply {
        findViewById<TextView>(R.id.name_textview_info).text = name
        findViewById<TextView>(R.id.address_textview_info).text = address
        findViewById<TextView>(R.id.go_textview_info).setOnClickListener {
            function(this)
        }
    }

    private fun changeMapType(): Boolean {
        currentType = if (currentType == BaiduMap.MAP_TYPE_NORMAL) {
            BaiduMap.MAP_TYPE_SATELLITE
        } else {
            BaiduMap.MAP_TYPE_NORMAL
        }
        baiduMap.mapType = currentType
        return true
    }

    private fun changeLocationMode() {
        currentMode = if (currentMode == MyLocationConfiguration.LocationMode.NORMAL) {
            MyLocationConfiguration.LocationMode.COMPASS
        } else {
            MyLocationConfiguration.LocationMode.NORMAL
        }
        baiduMap.apply {
            setMyLocationConfiguration(MyLocationConfiguration(currentMode, true, null))
            animateMapStatus(MapStatusUpdateFactory.newMapStatus(MapStatus.Builder().apply {
                overlook(0F)
            }.build()))
        }
    }

    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    //    初始化权限
    private fun initPermission() {
        var flag = true
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_DENIED) {
                flag = false
                break
            }
        }
        if (!flag) {
            requestCount++
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    //    回调检查权限是否成功
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCount > 2) finish()
        var flag = true
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                flag = false
                break
            }
        }
        if (!flag) {
            Snackbar.make(map_mapview_main, "必须给予权限才能正常运行", Snackbar.LENGTH_LONG).show()
            initPermission()
        }
    }

    override fun onPause() {
        map_mapview_main.onPause()
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        map_mapview_main.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI)
        super.onResume()
    }

    override fun onDestroy() {
        poiSearch.destroy()
        client.stop()
        baiduMap.isMyLocationEnabled = false
        map_mapview_main.onDestroy()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Suppress("DEPRECATION")
    override fun onSensorChanged(event: SensorEvent?) {
        val x = event!!.values[SensorManager.DATA_X]
        if (Math.abs(x - lastX) > 1.0) {
            direction = x
            baiduMap.setMyLocationData(MyLocationData.Builder().apply {
                accuracy(accuracy)
                direction(direction)
                latitude(latitude)
                longitude(longitude)
            }.build())
        }
        lastX = x
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && search_searchview_main.isOpen) {
            search_searchview_main.close()
            search_floatingactionbutton_main.visibility = View.VISIBLE
            return false
        }
        return super.onKeyDown(keyCode, event)
    }
}
