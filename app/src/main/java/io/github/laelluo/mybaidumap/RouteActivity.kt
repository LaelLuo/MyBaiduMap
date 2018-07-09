package io.github.laelluo.mybaidumap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.*
import kotlinx.android.synthetic.main.activity_route.*

class RouteActivity : AppCompatActivity(), SensorEventListener {
    private var nodeIndex = -1
    private lateinit var from: LatLng
    private lateinit var to: LatLng
    private lateinit var baiduMap: BaiduMap
    private lateinit var search: RoutePlanSearch
    private lateinit var line: WalkingRouteLine
    private lateinit var sensorManager: SensorManager
    private lateinit var client: LocationClient
    private var latitude = 0.00
    private var longitude = 0.00
    private var accuracy = 0F
    private var direction = 0F
    private var locationCount = 0
    private var lastX = 0F
    private var lastMarker: Overlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route)
        initSensor()
        initClient()
        initSearch()
        initData()
        initView()
        search.walkingSearch(WalkingRoutePlanOption().from(PlanNode.withLocation(from)).to(PlanNode.withLocation(to)))
    }

    private fun initSearch() {
        search = RoutePlanSearch.newInstance()
        search.setOnGetRoutePlanResultListener(object : OnGetRoutePlanResultListener {
            override fun onGetWalkingRouteResult(p0: WalkingRouteResult?) {
                p0?.apply {
                    if (this.error == SearchResult.ERRORNO.NO_ERROR && this.routeLines.size > 0) {
                        line = this.routeLines[0]
                        line.apply {
                            var lastPoint: LatLng? = null
                            allStep?.apply {
                                forEach { step ->
                                    //                                    step.entrance?.let { node ->
//                                        baiduMap.addOverlay(MarkerOptions().apply {
//                                            position(node.location)
//                                            zIndex(10)
//                                            anchor(0.5F, 0.5F)
//                                            icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
//                                        })
//                                    }
//                                    if (index == line.allStep.size - 1 && step.exit != null) {
//                                        baiduMap.addOverlay(MarkerOptions().apply {
//                                            position(step.exit.location)
//                                            zIndex(10)
//                                            anchor(0.5F, 0.5F)
//                                            icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_arrow))
//                                        })
//                                    }
                                    step.wayPoints?.let {
                                        val points = arrayListOf<LatLng>()
                                        lastPoint?.let { points.add(it) }
                                        points.addAll(it)
                                        baiduMap.addOverlay(PolylineOptions().apply {
                                            points(points)
                                            width(10)
                                            color(getColor(R.color.colorPrimary))
                                            zIndex(0)
                                        })
                                        lastPoint = it.last()
                                    }

                                }
                            }
                            starting?.apply {
                                val node = this
                                baiduMap.addOverlay(MarkerOptions().apply {
                                    position(node.location)
                                    zIndex(10)
                                    icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_start))
                                })
                            }
                            terminal?.apply {
                                val node = this
                                baiduMap.addOverlay(MarkerOptions().apply {
                                    position(node.location)
                                    zIndex(10)
                                    icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_end))
                                })
                            }
                        }
                        nodeIndex = 0
                        showInfo()
                    } else {
                        map_mapview_route.snack("抱歉，未找到结果")
                    }
                }
            }

            override fun onGetIndoorRouteResult(p0: IndoorRouteResult?) {}
            override fun onGetTransitRouteResult(p0: TransitRouteResult?) {}
            override fun onGetDrivingRouteResult(p0: DrivingRouteResult?) {}
            override fun onGetMassTransitRouteResult(p0: MassTransitRouteResult?) {}
            override fun onGetBikingRouteResult(p0: BikingRouteResult?) {}
        })
    }

    private fun showInfo() {
        baiduMap.animateMapStatus(MapStatusUpdateFactory.zoomTo(18F))
        lastMarker?.remove()
        lastMarker = baiduMap.addOverlay(MarkerOptions().apply {
            position(line.allStep[nodeIndex].entrance.location)
            zIndex(10)
            icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker))
        })
        info_textview_route.text = line.allStep[nodeIndex].instructions
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = intent.getStringExtra("name")
        }
        map_mapview_route.apply {
            baiduMap = map
            showZoomControls(false)
            showScaleControl(false)
            getChildAt(1)?.visibility = View.GONE
        }
        baiduMap.apply {
            setCompassEnable(false)
            uiSettings.isOverlookingGesturesEnabled = false
            setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS, true, null))
            isMyLocationEnabled = true
        }
        next_floatingactionbutton_route.setOnClickListener {
            if (nodeIndex < line.allStep.size - 1) {
                nodeIndex++
                showInfo()
            }
        }
        pre_floatingactionbutton_route.setOnClickListener {
            if (nodeIndex > 0) {
                nodeIndex--
                showInfo()
            }
        }
    }

    private fun initData() {
        intent.apply {
            getDoubleArrayExtra("from").apply {
                from = LatLng(this[0], this[1])
            }
            getDoubleArrayExtra("to").apply {
                to = LatLng(this[0], this[1])
            }
        }
    }

    override fun onPause() {
        map_mapview_route.onPause()
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        map_mapview_route.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI)
        super.onResume()
    }

    override fun onDestroy() {
        search.destroy()
        client.stop()
        baiduMap.isMyLocationEnabled = false
        map_mapview_route.onDestroy()
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

}
