package com.fancynavi.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.here.android.mpa.common.CopyrightLogoPosition;
import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.MapSettings;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.common.PositioningManager.OnPositionChangedListener;
import com.here.android.mpa.common.RoadElement;
import com.here.android.mpa.common.ViewObject;
import com.here.android.mpa.guidance.LaneInformation;
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.guidance.SafetySpotNotification;
import com.here.android.mpa.guidance.SafetySpotNotificationInfo;
import com.here.android.mpa.guidance.TrafficNotification;
import com.here.android.mpa.guidance.VoiceCatalog;
import com.here.android.mpa.guidance.VoiceGuidanceOptions;
import com.here.android.mpa.mapping.LocalMesh;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapGesture;
import com.here.android.mpa.mapping.MapLocalModel;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.mapping.MapState;
import com.here.android.mpa.mapping.PositionIndicator;
import com.here.android.mpa.mapping.SupportMapFragment;
import com.here.android.mpa.mapping.customization.CustomizableScheme;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.DynamicPenalty;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;
import com.here.msdkui.guidance.GuidanceManeuverData;
import com.here.msdkui.guidance.GuidanceManeuverListener;
import com.here.msdkui.guidance.GuidanceManeuverPresenter;
import com.here.msdkui.guidance.GuidanceManeuverView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static java.util.Locale.TRADITIONAL_CHINESE;

class MapFragmentView {
    static Map m_map;
    static GeoPosition currentGeoPosition;
    NavigationManager m_navigationManager;
    boolean isRoadView = false;
    boolean isDragged = false;
    private boolean safetyCameraAhead = false;
    private GeoCoordinate safetyCameraLocation;
    private double distanceToSafetyCamera;
    private double safetyCameraSpeedLimit;
    private ImageView safetyCamImageView;
    private TextView safetyCamTextView;
    private TextView safetyCamSpeedTextView;
    private boolean isRouteOverView;
    private PositioningManager m_positioningManager;
    private AppCompatActivity m_activity;
    private PositionIndicator positionIndicator;
    private SupportMapFragment supportMapFragment;
    private VoiceActivation voiceActivation;
    private Button m_naviControlButton;
    private Button clearButton;
    private Button northUpButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button carRouteButton;
    private Button truckRouteButton;
    private Button scooterRouteButton;
    private Button bikeRouteButton;
    private Button pedsRouteButton;
    private Button trafficButton;
    private boolean trafficEnabled;
    private ProgressBar progressBar;
    private TextView calculatingTextView;
    private Route m_route;
    private MapRoute mapRoute;
    private GeoBoundingBox mapRouteBBox;
    private MapLocalModel mapLocalModel;
    private boolean m_foregroundServiceStarted;
    private CoreRouter coreRouter;
    //HERE SDK UI KIT components
    private GuidanceManeuverView guidanceManeuverView;
    private GuidanceManeuverPresenter guidanceManeuverPresenter;
    private ArrayList<GeoCoordinate> waypointList = new ArrayList<>();
    private ArrayList<MapMarker> userInputWaypoints = new ArrayList<>();
    private ArrayList<MapMarker> wayPointIcons = new ArrayList<>();


    //HERE UI Kit, Guidance Maneuver View
    private GuidanceManeuverListener guidanceManeuverListener = new GuidanceManeuverListener() {
        @Override
        public void onDataChanged(@Nullable GuidanceManeuverData guidanceManeuverData) {
            guidanceManeuverView.setManeuverData(guidanceManeuverData);
            /*
            if (guidanceManeuverData != null) {
                Log.d("Test", "guidanceManeuverData.getInfo1(): " + guidanceManeuverData.getInfo1());
                Log.d("Test", "guidanceManeuverData.getInfo2(): " + guidanceManeuverData.getInfo2());
            }
            */
        }

        @Override
        public void onDestinationReached() {
        }
    };
    private long simulationSpeedMs = 20; //defines the speed of navigation simulation
    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            shiftMapCenter(m_map);
        }
    };
    private ImageView junctionViewImageView;
    private ImageView signpostImageView;
    private OnTouchListener mapOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (m_navigationManager.getMapUpdateMode() != NavigationManager.MapUpdateMode.NONE) {
                isRoadView = false;
                m_navigationManager.setMapUpdateMode(NavigationManager.MapUpdateMode.NONE);
                resetMapCenter(m_map);
                m_map.setTilt(0);
                m_map.zoomTo(mapRouteBBox, Map.Animation.LINEAR, 0f);
            }
            return false;
        }
    };
    private NavigationManager.NavigationManagerEventListener m_navigationManagerEventListener = new NavigationManager.NavigationManagerEventListener() {
        @Override
        public void onRunningStateChanged() {
            Toast.makeText(m_activity, "Running state changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onNavigationModeChanged() {
            Toast.makeText(m_activity, "Navigation mode changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onEnded(NavigationManager.NavigationMode navigationMode) {
            Toast.makeText(m_activity, navigationMode + " was ended", Toast.LENGTH_SHORT).show();
//            m_map.setMapScheme(Map.Scheme.CARNAV_TRAFFIC_DAY);
//            resetMapCenter(m_map);
//            m_map.removeMapObject(mapLocalModel);
//            m_map.setTilt(0);
//            m_map.zoomTo(mapRouteBBox, Map.Animation.NONE, 0f);
//            View guidanceView = m_activity.findViewById(R.id.guidanceManeuverView);
//            guidanceView.setVisibility(View.GONE);
////            if (mapRoute != null) {
////                m_map.removeMapObject(mapRoute);
////            }
            stopForegroundService();
//            startNavigation();
        }

        @Override
        public void onMapUpdateModeChanged(NavigationManager.MapUpdateMode mapUpdateMode) {
            //Toast.makeText(m_activity, "Map update mode is changed to " + mapUpdateMode, Toast.LENGTH_SHORT).show();
            Log.d("Test", "mapUpdateMode is: " + mapUpdateMode);
        }

        @Override
        public void onRouteUpdated(Route route) {
            resetMapRoute(route);
        }

        @Override
        public void onCountryInfo(String s, String s1) {
            Toast.makeText(m_activity, "Country info updated from " + s + " to " + s1,
                    Toast.LENGTH_SHORT).show();
        }
    };
    private NavigationManager.NewInstructionEventListener m_newInstructionEventListener = new NavigationManager.NewInstructionEventListener() {
        @Override
        public void onNewInstructionEvent() {
            Log.d("Test", "getNextManeuver().getRoadName(): " + m_navigationManager.getNextManeuver().getRoadName());
            Log.d("Test", "getNextManeuver().getNextRoadName(): " + m_navigationManager.getNextManeuver().getNextRoadName());
        }
    };
    private NavigationManager.SafetySpotListener safetySpotListener = new NavigationManager.SafetySpotListener() {
        @Override
        public void onSafetySpot(SafetySpotNotification safetySpotNotification) {
            super.onSafetySpot(safetySpotNotification);
            List<SafetySpotNotificationInfo> safetySpotInfos = safetySpotNotification.getSafetySpotNotificationInfos();
            for (int i = 0; i < safetySpotInfos.size(); i++) {
                SafetySpotNotificationInfo safetySpotInfo = safetySpotInfos.get(i);
                safetyCameraLocation = safetySpotInfo.getSafetySpot().getCoordinate();
                distanceToSafetyCamera = safetySpotInfo.getDistance();
                safetyCameraSpeedLimit = safetySpotInfo.getSafetySpot().getSpeedLimit1();
                safetyCameraAhead = true;
            }
        }
    };

    private GeoCoordinate lastKnownLocation;

    private OnPositionChangedListener positionListener = new OnPositionChangedListener() {
        @Override
        public void onPositionUpdated(PositioningManager.LocationMethod locationMethod, GeoPosition geoPosition, boolean b) {
            currentGeoPosition = geoPosition;
            if (!isRouteOverView) {
                if (!isDragged) {
                    m_map.setCenter(currentGeoPosition.getCoordinate(), Map.Animation.NONE);
                }
            }
            if (safetyCameraAhead) {
                if (lastKnownLocation.distanceTo(safetyCameraLocation) < geoPosition.getCoordinate().distanceTo(safetyCameraLocation)) {
                    safetyCameraAhead = false;
                    safetyCamImageView.setVisibility(View.INVISIBLE);
                    safetyCamTextView.setVisibility(View.INVISIBLE);
                    safetyCamSpeedTextView.setVisibility(View.INVISIBLE);
                } else {
                    safetyCamImageView.setVisibility(View.VISIBLE);
                    safetyCamTextView.setVisibility(View.VISIBLE);
                    safetyCamSpeedTextView.setVisibility(View.VISIBLE);
                    safetyCamTextView.setText((int) geoPosition.getCoordinate().distanceTo(safetyCameraLocation) + "m");
                    safetyCamSpeedTextView.setText(Math.round(safetyCameraSpeedLimit * 3.6) + "km/h");
                }
            }
            if (lastKnownLocation != null) {
                if (lastKnownLocation.distanceTo(geoPosition.getCoordinate()) > 0) {
                    lastKnownLocation = geoPosition.getCoordinate();
                }
            } else {
                lastKnownLocation = geoPosition.getCoordinate();
            }
        }

        @Override
        public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
            Log.d("Test", "locationMethod: " + locationMethod + " locationStatus: " + locationStatus);
        }
    };

    private MapGesture.OnGestureListener customOnGestureListener = new MapGesture.OnGestureListener() {

        @Override
        public void onPanStart() {
            isDragged = true;
        }

        @Override
        public void onPanEnd() {

        }

        @Override
        public void onMultiFingerManipulationStart() {

        }

        @Override
        public void onMultiFingerManipulationEnd() {

        }

        @Override
        public boolean onMapObjectsSelected(List<ViewObject> list) {
            return false;
        }

        @Override
        public boolean onTapEvent(PointF pointF) {
            return false;
        }

        @Override
        public boolean onDoubleTapEvent(PointF pointF) {
            touchToAddWaypoint(pointF);
            return true;
        }

        @Override
        public void onPinchLocked() {

        }

        @Override
        public boolean onPinchZoomEvent(float v, PointF pointF) {
            return false;
        }

        @Override
        public void onRotateLocked() {

        }

        @Override
        public boolean onRotateEvent(float v) {
            return false;
        }

        @Override
        public boolean onTiltEvent(float v) {
            return false;
        }

        @Override
        public boolean onLongPressEvent(PointF pointF) {
            return false;
        }

        @Override
        public void onLongPressRelease() {

        }

        @Override
        public boolean onTwoFingerTapEvent(PointF pointF) {
            return false;
        }
    };
    private NavigationManager.PositionListener m_positionListener = new NavigationManager.PositionListener() {
        @Override
        public void onPositionUpdated(GeoPosition geoPosition) {
            mapLocalModel.setAnchor(geoPosition.getCoordinate());
            mapLocalModel.setYaw((float) geoPosition.getHeading());
        }
    };
    private NavigationManager.LaneInformationListener m_LaneInformationListener = new NavigationManager.LaneInformationListener() {
        @Override
        public void onLaneInformation(List<LaneInformation> list, RoadElement roadElement) {
            super.onLaneInformation(list, roadElement);
            /*
            Log.d("Test", "=======================================================================================");
            Log.d("Test", "Lane information");
            Log.d("Test", "---------------------------------------------------------------------------------------");
            for (LaneInformation laneInformation : list) {
                Log.d("Test", "Lane Directions " + laneInformation.getDirections());
                Log.d("Test", "Recommended " + laneInformation.getRecommendationState());
            }
            */
        }
    };
    private NavigationManager.RealisticViewListener m_realisticViewListener = new NavigationManager.RealisticViewListener() {
        @Override
        public void onRealisticViewNextManeuver(NavigationManager.AspectRatio aspectRatio, Image junction, Image signpost) {
        }

        @Override
        public void onRealisticViewShow(NavigationManager.AspectRatio aspectRatio, Image junction, Image signpost) {
            View mainLinearLayout = m_activity.findViewById(R.id.main_linear_layout);
            junctionViewImageView.requestLayout();
            signpostImageView.requestLayout();
            int jvViewWidth = (int) (mainLinearLayout.getWidth() / 2.5);
            int jvViewHeight;
            switch (aspectRatio) {
                case AR_16x9:
                    jvViewHeight = jvViewWidth / 16 * 9;
                    break;
                case AR_5x3:
                    jvViewHeight = jvViewWidth / 5 * 3;
                    break;
                case AR_4x3:
                    jvViewHeight = jvViewWidth / 4 * 3;
                    break;
                default:
                    jvViewHeight = jvViewWidth * 2;
            }
            junctionViewImageView.getLayoutParams().height = jvViewHeight;
            junctionViewImageView.getLayoutParams().width = jvViewWidth;
            signpostImageView.getLayoutParams().height = (int) (jvViewHeight * 0.75);
            signpostImageView.getLayoutParams().width = jvViewWidth;
            Bitmap junctionBitmap = junction.getBitmap((int) junction.getWidth(), (int) junction.getHeight());
            Bitmap signpostBitMap = signpost.getBitmap((int) signpost.getWidth(), (int) signpost.getHeight());
            junctionViewImageView.setImageBitmap(junctionBitmap);
            signpostImageView.setImageBitmap(signpostBitMap);
            junctionViewImageView.setVisibility(View.VISIBLE);
            signpostImageView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onRealisticViewHide() {
            junctionViewImageView.setVisibility(View.GONE);
            signpostImageView.setVisibility(View.GONE);
        }
    };

    MapFragmentView(AppCompatActivity activity) {
        m_activity = activity;
        initSupportMapFragment();
    }

    private static PointF getMapMarkerAnchorPoint(MapMarker mapMarker) {
        int iconHeight = (int) mapMarker.getIcon().getHeight();
        int iconWidth = (int) mapMarker.getIcon().getWidth();
        return new PointF((float) (iconWidth / 2), (float) iconHeight);
    }

    private void resetMapRoute(Route route) {
        if (mapRoute != null) {
            m_map.removeMapObject(mapRoute);
        }
        mapRoute = new MapRoute(route);
        mapRoute.setColor(Color.argb(255, 243, 174, 255)); //F3AEFF
        mapRoute.setOutlineColor(Color.argb(255, 78, 0, 143)); //4E008F
        mapRoute.setTraveledColor(Color.DKGRAY);
        mapRoute.setUpcomingColor(Color.LTGRAY);
        mapRoute.setTrafficEnabled(true);
        m_map.addMapObject(mapRoute);
    }

    private void touchToAddWaypoint(PointF p) {
        isDragged = true;
        GeoCoordinate touchPointGeoCoordinate = m_map.pixelToGeo(p);
        MapMarker mapMarker = new MapMarker(touchPointGeoCoordinate);
        mapMarker.setDraggable(true);
        userInputWaypoints.add(mapMarker);
        mapMarker.setAnchorPoint(getMapMarkerAnchorPoint(mapMarker));
        m_map.addMapObject(mapMarker);
        carRouteButton.setVisibility(View.VISIBLE);
        truckRouteButton.setVisibility(View.VISIBLE);
        scooterRouteButton.setVisibility(View.VISIBLE);
        bikeRouteButton.setVisibility(View.VISIBLE);
        pedsRouteButton.setVisibility(View.VISIBLE);
        m_naviControlButton.setVisibility(View.VISIBLE);
        clearButton.setVisibility(View.VISIBLE);
    }

    private void initSupportMapFragment() {
        /* Locate the mapFragment UI element */
        supportMapFragment = getMapFragment();
        supportMapFragment.setCopyrightLogoPosition(CopyrightLogoPosition.BOTTOM_CENTER);
        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath() + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = m_activity.getPackageManager().getApplicationInfo(m_activity.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            intentName = bundle.getString("INTENT_NAME");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
        }


        boolean success = MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot, intentName);
        if (!success) {
            // Setting the isolated disk cache was not successful, please check if the path is valid and
            // ensure that it does not match the default location
            // (getExternalStorageDirectory()/.here-maps).
            // Also, ensure the provided intent name does not match the default intent name.
        } else {
            if (supportMapFragment != null) {
                /* Initialize the MapFragment, results will be given via the called back. */
                supportMapFragment.init(new OnEngineInitListener() {
                    @Override
                    public void onEngineInitializationCompleted(Error error) {
                        if (error == Error.NONE) {
                            supportMapFragment.getMapGesture().addOnGestureListener(customOnGestureListener, 0, false);
                            m_map = supportMapFragment.getMap();
                            initJunctionView();
                            m_map.setMapScheme(Map.Scheme.CARNAV_DAY);
                            m_map.setMapDisplayLanguage(TRADITIONAL_CHINESE);
                            m_map.setSafetySpotsVisible(true);
                            m_map.setExtrudedBuildingsVisible(false);
                            m_map.setLandmarksVisible(true);

                            m_map.addTransformListener(new Map.OnTransformListener() {
                                @Override
                                public void onMapTransformStart() {
                                    isDragged = true;
                                }

                                @Override
                                public void onMapTransformEnd(MapState mapState) {

                                }
                            });

                            /* Listeners of map buttons */
                            northUpButton = m_activity.findViewById(R.id.north_up);
                            northUpButton.setOnClickListener(v -> {
                                m_map.setOrientation(0);
                                m_map.setTilt(0);
                                resetMapCenter(m_map);
                                if (!isRouteOverView) {
                                    m_map.setCenter(m_positioningManager.getPosition().getCoordinate(), Map.Animation.LINEAR);
                                } else {
                                    m_map.zoomTo(mapRouteBBox, Map.Animation.LINEAR, Map.MOVE_PRESERVE_ORIENTATION);
                                }

                            });
                            zoomInButton = m_activity.findViewById(R.id.zoom_in);
                            zoomInButton.setOnClickListener(v -> {
                                double zoomLevel = m_map.getZoomLevel();
                                m_map.setZoomLevel(zoomLevel + 1);
                            });
                            zoomOutButton = m_activity.findViewById(R.id.zoom_out);
                            zoomOutButton.setOnClickListener(v -> {
                                double zoomLevel = m_map.getZoomLevel();
                                m_map.setZoomLevel(zoomLevel - 1);
                            });
                            carRouteButton = m_activity.findViewById(R.id.car_route);
                            truckRouteButton = m_activity.findViewById(R.id.truck_route);
                            scooterRouteButton = m_activity.findViewById(R.id.scooter_route);
                            bikeRouteButton = m_activity.findViewById(R.id.bike_route);
                            pedsRouteButton = m_activity.findViewById(R.id.peds_route);
                            carRouteButton.setOnClickListener(vCarRouteButton -> {
                                calculateRoute(prepareRouteOptions(RouteOptions.TransportMode.CAR));
                            });
                            truckRouteButton.setOnClickListener(vTruckRouteButton -> {
                                calculateRoute(prepareRouteOptions(RouteOptions.TransportMode.TRUCK));
                            });
                            scooterRouteButton.setOnClickListener(vScooterRouteButton -> {
                                calculateRoute(prepareRouteOptions(RouteOptions.TransportMode.SCOOTER));
                            });
                            bikeRouteButton.setOnClickListener(vBikeRouteButton -> {
                                calculateRoute(prepareRouteOptions(RouteOptions.TransportMode.BICYCLE));
                            });
                            pedsRouteButton.setOnClickListener(vPedsRouteButton -> {
                                calculateRoute(prepareRouteOptions(RouteOptions.TransportMode.PEDESTRIAN));
                            });
                            trafficButton = m_activity.findViewById(R.id.traffic_button);
                            trafficButton.setTextColor(Color.parseColor("#FF000000"));
                            trafficButton.setOnClickListener(v -> {
                                if (!m_map.isTrafficInfoVisible()) {
                                    trafficEnabled = true;
                                    m_map.setTrafficInfoVisible(true);
                                    if (m_map.getMapScheme().equals(Map.Scheme.CARNAV_DAY)) {
                                        m_map.setMapScheme(Map.Scheme.CARNAV_TRAFFIC_DAY);
                                    }
                                    trafficButton.setTextColor(Color.parseColor("#FFFF0000"));
                                } else {
                                    trafficEnabled = false;
                                    m_map.setTrafficInfoVisible(false);
                                    if (m_map.getMapScheme().equals(Map.Scheme.CARNAV_TRAFFIC_DAY)) {
                                        m_map.setMapScheme(Map.Scheme.CARNAV_DAY);
                                    }
                                    trafficButton.setTextColor(Color.parseColor("#FF000000"));
                                }
                            });
                            m_naviControlButton = m_activity.findViewById(R.id.startGuidance);
                            m_naviControlButton.setText("Create Route");
                            m_naviControlButton.setOnClickListener(v -> {
                                if (m_route != null) {
                                    m_navigationManager.stop();
                                    m_map.removeMapObject(mapLocalModel);
                                    resetMapCenter(m_map);
                                    m_map.setTilt(0);
                                    guidanceManeuverPresenter.pause();
                                    startNavigation();
                                }
                            });
                            clearButton = m_activity.findViewById(R.id.clear);
                            clearButton.setOnClickListener(v -> resetMap());

                            safetyCamImageView = m_activity.findViewById(R.id.safety_cam_image_view);
                            safetyCamTextView = m_activity.findViewById(R.id.safety_cam_text_view);
                            safetyCamSpeedTextView = m_activity.findViewById(R.id.safety_cam_speed_text_view);

                            /* PositioningManager init */
                            m_positioningManager = PositioningManager.getInstance();

                            /* Advanced positioning */
                            /* Disable to run on emulator */
//                            LocationDataSourceHERE m_hereDataSource;
//                            m_hereDataSource = LocationDataSourceHERE.getInstance();
//                            m_positioningManager.setDataSource(m_hereDataSource);
                            m_positioningManager.addListener(new WeakReference<>(positionListener));

                            /* GPS logging function */
//                            EnumSet<PositioningManager.LogType> logTypes = EnumSet.of(
//                                    PositioningManager.LogType.RAW,
//                                    PositioningManager.LogType.DATA_SOURCE
//                            );
//                            m_positioningManager.setLogType(logTypes);

                            /* Start tracking position */
                            if (m_positioningManager != null) {
                                m_positioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR);
                            }

                            /* Show position indicator */
                            positionIndicator = m_map.getPositionIndicator();
                            positionIndicator.setVisible(true);
                            positionIndicator.setAccuracyIndicatorVisible(true);

                            /* Download voice */
                            voiceActivation = new VoiceActivation();
                            voiceActivation.setContext(m_activity);
                            String desiredVoiceLanguageCode = "CHT";
                            voiceActivation.setDesiredLangCode(desiredVoiceLanguageCode);
                            voiceActivation.downloadCatalogAndSkin();
                        } else {
                            Toast.makeText(m_activity, "ERROR: Cannot initialize Map with error " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }
    }

    // Google has deprecated android.app.Fragment class. It is used in current SDK implementation.
    // Will be fixed in future SDK version.
    private SupportMapFragment getMapFragment() {
        return (SupportMapFragment) m_activity.getSupportFragmentManager().findFragmentById(R.id.mapFragmentView);
    }

    /*
     * Android 8.0 (API level 26) limits how frequently background apps can retrieve the user's
     * current location. Apps can receive location updates only a few times each hour.
     * See href="https://developer.android.com/about/versions/oreo/background-location-limits.html
     * In order to retrieve location updates more frequently start a foreground service.
     * See https://developer.android.com/guide/components/services.html#Foreground
     */
    private void startForegroundService() {
        if (!m_foregroundServiceStarted) {
            m_foregroundServiceStarted = true;
            Intent startIntent = new Intent(m_activity, ForegroundService.class);
            startIntent.setAction(ForegroundService.START_ACTION);
            m_activity.getApplicationContext().startService(startIntent);
        }
    }

    private void stopForegroundService() {
        if (m_foregroundServiceStarted) {
            m_foregroundServiceStarted = false;
            Intent stopIntent = new Intent(m_activity, ForegroundService.class);
            stopIntent.setAction(ForegroundService.STOP_ACTION);
            m_activity.getApplicationContext().startService(stopIntent);
        }
    }


    private void hudMapScheme(Map map) {
        /*Map Customization - Start*/
        CustomizableScheme m_colorScheme;
        String m_colorSchemeName = "colorScheme";
        if (map != null && map.getCustomizableScheme(m_colorSchemeName) == null) {
            map.createCustomizableScheme(m_colorSchemeName, Map.Scheme.CARNAV_NIGHT_GREY);
            map.setMapScheme(Map.Scheme.CARNAV_NIGHT_GREY);
            /*
            m_colorScheme = map.getCustomizableScheme(m_colorSchemeName);
            ZoomRange range = new ZoomRange(0.0, 20.0);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY0_WIDTH, 20, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY1_WIDTH, 20, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY2_WIDTH, 20, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY3_WIDTH, 20, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY4_WIDTH, 20, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY0_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY1_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY2_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY3_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY4_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY0_TUNNELCOLOR, Color.GRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY1_TUNNELCOLOR, Color.GRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY2_TUNNELCOLOR, Color.GRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY3_TUNNELCOLOR, Color.GRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY4_TUNNELCOLOR, Color.GRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY0_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY1_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY2_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY3_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY4_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);
            m_colorScheme.setVariableValue(CustomizableVariables.Street.CATEGORY4_STREETPOLYLINEATTRIBUTE_TOLL_COLOR, Color.LTGRAY, range);

            map.setMapScheme(m_colorScheme);
            map.setLandmarksVisible(false);
            map.setExtrudedBuildingsVisible(false);
            map.setCartoMarkersVisible(false);
            */
            EnumSet<Map.LayerCategory> invisibleLayerCategory = EnumSet.of(
                    Map.LayerCategory.ABSTRACT_CITY_MODEL
            );
            map.setVisibleLayers(invisibleLayerCategory, false);

        } else {
            if (map != null) {
                map.setMapScheme(Map.Scheme.CARNAV_NIGHT);
            }
        }
        /*Map Customization - End*/
    }

    private MapLocalModel createPosition3dObj() {
        MapLocalModel mapLocalModel = new MapLocalModel();
        LocalModelLoader localModelLoader = new LocalModelLoader(m_activity);
        LocalMesh localMesh = new LocalMesh();
        localMesh.setVertices(localModelLoader.getObjVertices());
        localMesh.setVertexIndices(localModelLoader.getObjIndices());
        localMesh.setTextureCoordinates(localModelLoader.getObjTexCoords());

        mapLocalModel.setMesh(localMesh);
        Image image = null;
        try {
            image = new Image();
            image.setImageResource(R.drawable.grad);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mapLocalModel.setTexture(image); //an Image object
        mapLocalModel.setScale(6.0f);
        mapLocalModel.setDynamicScalingEnabled(true);

        m_map.addMapObject(mapLocalModel);
        return mapLocalModel;
    }

    private void initJunctionView() {
        junctionViewImageView = m_activity.findViewById(R.id.junctionImageView);
        junctionViewImageView.setVisibility(View.GONE);
        signpostImageView = m_activity.findViewById(R.id.signpostImageView);
        signpostImageView.setVisibility(View.GONE);

    }

    private void initGuidanceManeuverView(Route route) {
        guidanceManeuverView = m_activity.findViewById(R.id.guidanceManeuverView);
        guidanceManeuverPresenter = new GuidanceManeuverPresenter(m_activity.getApplicationContext(), m_navigationManager, route);
        guidanceManeuverPresenter.addListener(guidanceManeuverListener);
    }

    void shiftMapCenter(Map map) {
        map.setTransformCenter(new PointF(
                (float) (map.getWidth() * 0.5),
                (float) (map.getHeight() * 0.8)
        ));
    }

    private void resetMapCenter(Map map) {
        map.setTransformCenter(new PointF(
                (float) (map.getWidth() * 0.5),
                (float) (map.getHeight() * 0.5)
        ));
    }

    private void intoNavigationMode() {
        zoomInButton.setVisibility(View.INVISIBLE);
        zoomOutButton.setVisibility(View.INVISIBLE);
        northUpButton.setVisibility(View.INVISIBLE);
        carRouteButton.setVisibility(View.INVISIBLE);
        truckRouteButton.setVisibility(View.INVISIBLE);
        scooterRouteButton.setVisibility(View.INVISIBLE);
        bikeRouteButton.setVisibility(View.INVISIBLE);
        pedsRouteButton.setVisibility(View.INVISIBLE);


        positionIndicator.setVisible(false);
        positionIndicator.setAccuracyIndicatorVisible(false);

        guidanceManeuverPresenter.resume();
        m_activity.findViewById(R.id.guidanceManeuverView).setVisibility(View.VISIBLE);
        m_navigationManager.setMapUpdateMode(NavigationManager.MapUpdateMode.ROADVIEW);
        isRoadView = true;
        m_navigationManager.setRealisticViewMode(NavigationManager.RealisticViewMode.DAY);
        EnumSet<NavigationManager.NaturalGuidanceMode> naturalGuidanceModes = EnumSet.of(
                NavigationManager.NaturalGuidanceMode.JUNCTION,
                NavigationManager.NaturalGuidanceMode.STOP_SIGN,
                NavigationManager.NaturalGuidanceMode.TRAFFIC_LIGHT
        );
        m_navigationManager.setTrafficAvoidanceMode(NavigationManager.TrafficAvoidanceMode.DYNAMIC);
        m_navigationManager.setNaturalGuidanceMode(naturalGuidanceModes);
        shiftMapCenter(m_map);
        //hudMapScheme(m_map);
        m_map.setTilt(60);
        m_navigationManager.startNavigation(m_route);
        m_positioningManager.setMapMatchingEnabled(true);

        /* Voice Guidance init */
        VoiceCatalog voiceCatalog = voiceActivation.getVoiceCatalog();
        VoiceGuidanceOptions voiceGuidanceOptions = m_navigationManager.getVoiceGuidanceOptions();
        voiceGuidanceOptions.setVoiceSkin(voiceCatalog.getLocalVoiceSkin(voiceActivation.getDesiredVoiceId()));
        EnumSet<NavigationManager.AudioEvent> audioEventEnumSet = EnumSet.of(
                NavigationManager.AudioEvent.MANEUVER,
                NavigationManager.AudioEvent.ROUTE,
                NavigationManager.AudioEvent.SAFETY_SPOT,
                NavigationManager.AudioEvent.SPEED_LIMIT,
                NavigationManager.AudioEvent.GPS
        );
        m_navigationManager.setEnabledAudioEvents(audioEventEnumSet);

        supportMapFragment.setOnTouchListener(mapOnTouchListener);
        supportMapFragment.getMapGesture().removeOnGestureListener(customOnGestureListener);
        mapLocalModel = createPosition3dObj();
    }

    private void startNavigation() {

        initGuidanceManeuverView(m_route);
        resetMapRoute(m_route);


        resetMapCenter(m_map);
        m_map.setTilt(0);
        m_navigationManager.setMap(m_map);
        m_map.zoomTo(mapRouteBBox, Map.Animation.NONE, 0f);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(m_activity);
        alertDialogBuilder.setTitle("Navigation");
        alertDialogBuilder.setMessage("Choose Mode");
        alertDialogBuilder.setNegativeButton("Navigation", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int i) {
                m_naviControlButton.setText("Stop Navi");
                intoNavigationMode();
                isRouteOverView = false;
                NavigationManager.Error error = m_navigationManager.startNavigation(m_route);
                Log.e("Error: ", error.toString());
                Toast.makeText(m_activity, "Error: " + error.toString(), Toast.LENGTH_SHORT).show();
                startForegroundService();
            }
        });
        alertDialogBuilder.setPositiveButton("Simulation", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int i) {
                m_naviControlButton.setText("Stop Navi");
                intoNavigationMode();
                isRouteOverView = false;
                NavigationManager.Error error = m_navigationManager.simulate(m_route, simulationSpeedMs);
                Toast.makeText(m_activity, "Error: " + error.toString(), Toast.LENGTH_SHORT).show();
                startForegroundService();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        addNavigationListeners();
    }

    private void resetMap() {
        isRouteOverView = false;
        if (coreRouter != null) {
            if (coreRouter.isBusy()) {
                coreRouter.cancel();
            }
        }
        supportMapFragment.setOnTouchListener(null);
        positionIndicator.setVisible(true);
        positionIndicator.setAccuracyIndicatorVisible(true);
        if (m_navigationManager != null) {
            if (m_navigationManager.getRunningState() == NavigationManager.NavigationState.RUNNING) {
                m_navigationManager.stop();
            }
        }
        m_naviControlButton.setText("Create Route");
        m_route = null;
        if (guidanceManeuverPresenter != null) {
            guidanceManeuverPresenter.pause();
        }
        if (m_map.isTrafficInfoVisible()) {
            m_map.setMapScheme(Map.Scheme.CARNAV_TRAFFIC_DAY);
        } else {
            m_map.setMapScheme(Map.Scheme.CARNAV_DAY);
        }

        resetMapCenter(m_map);

        m_map.removeMapObject(mapLocalModel);
        m_map.setTilt(0);
        m_activity.findViewById(R.id.guidanceManeuverView).setVisibility(View.GONE);
        if (mapRoute != null) {
            m_map.removeMapObject(mapRoute);
        }
        if (!userInputWaypoints.isEmpty()) {
            for (MapMarker mkr : userInputWaypoints) {
                m_map.removeMapObject(mkr);
            }
        }
        if (!wayPointIcons.isEmpty()) {
            for (MapMarker mkr : wayPointIcons) {
                m_map.removeMapObject(mkr);
            }
        }
        wayPointIcons.clear();
        userInputWaypoints.clear();
        waypointList.clear();
        isDragged = false;

        northUpButton.callOnClick();
        supportMapFragment.getMapGesture().addOnGestureListener(customOnGestureListener, 0, false);
        zoomInButton.setVisibility(View.VISIBLE);
        zoomOutButton.setVisibility(View.VISIBLE);
        northUpButton.setVisibility(View.VISIBLE);
        carRouteButton.setVisibility(View.INVISIBLE);
        truckRouteButton.setVisibility(View.INVISIBLE);
        scooterRouteButton.setVisibility(View.INVISIBLE);
        bikeRouteButton.setVisibility(View.INVISIBLE);
        pedsRouteButton.setVisibility(View.INVISIBLE);
        m_naviControlButton.setVisibility(View.INVISIBLE);
        clearButton.setVisibility(View.INVISIBLE);
    }

    private RouteOptions prepareRouteOptions(RouteOptions.TransportMode transportMode) {
        RouteOptions routeOptions = new RouteOptions();
        switch (transportMode) {
            case CAR:
                routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
                routeOptions.setHighwaysAllowed(true);
                if (!trafficEnabled) {
                    m_map.setMapScheme(Map.Scheme.CARNAV_DAY);
                } else {
                    m_map.setMapScheme(Map.Scheme.CARNAV_TRAFFIC_DAY);
                }
                break;
            case TRUCK:
                routeOptions.setTransportMode(RouteOptions.TransportMode.TRUCK);
                routeOptions.setHighwaysAllowed(true);
                m_map.setMapScheme(Map.Scheme.TRUCK_DAY);
                break;
            case SCOOTER:
                routeOptions.setTransportMode(RouteOptions.TransportMode.SCOOTER);
                if (!trafficEnabled) {
                    m_map.setMapScheme(Map.Scheme.CARNAV_DAY);
                } else {
                    m_map.setMapScheme(Map.Scheme.CARNAV_TRAFFIC_DAY);
                }
                routeOptions.setHighwaysAllowed(false);
                break;
            case BICYCLE:
                routeOptions.setTransportMode(RouteOptions.TransportMode.BICYCLE);
                m_map.setMapScheme(Map.Scheme.TERRAIN_DAY);
                routeOptions.setHighwaysAllowed(false);
                break;
            case PEDESTRIAN:
                routeOptions.setTransportMode(RouteOptions.TransportMode.PEDESTRIAN);
                m_map.setMapScheme(Map.Scheme.PEDESTRIAN_DAY);
                routeOptions.setHighwaysAllowed(false);
                break;
        }
        routeOptions.setRouteType(RouteOptions.Type.FASTEST);
        routeOptions.setRouteCount(1);
        return routeOptions;
    }

    private void retryRouting(Context context, RoutingError routingError, RouteOptions m_routeOptions) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle("Route Calculation Failed:\n" + routingError.name());
        alertDialogBuilder.setNegativeButton("Retry", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int i) {
                calculateRoute(m_routeOptions);
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void calculateRoute(RouteOptions routeOptions) {
        HereRouter hereRouter = new HereRouter(routeOptions);
        hereRouter.setContext(m_activity);
        if (wayPointIcons.size() == 0) {
            for (int i = 0; i < userInputWaypoints.size(); i++) {
                MapMarker mapMarker = userInputWaypoints.get(i);
                waypointList.add(mapMarker.getCoordinate());
                m_map.removeMapObject(mapMarker);
            }
            if (mapRoute != null) {
                m_map.removeMapObject(mapRoute);
            }
            wayPointIcons = hereRouter.getOutputWaypointIcons();
        }
        hereRouter.setWaypoints(waypointList);

        hereRouter.createRoute();
        for (MapMarker m : wayPointIcons) {
            m_map.addMapObject(m);
            m.setAnchorPoint(getMapMarkerAnchorPoint(m));
        }
        Log.d("Test", "wayPointIcons: " + wayPointIcons.size());

        coreRouter = new CoreRouter();
        if (m_map.isTrafficInfoVisible()) {
            DynamicPenalty dynamicPenalty = new DynamicPenalty();
            dynamicPenalty.setTrafficPenaltyMode(Route.TrafficPenaltyMode.OPTIMAL);
            coreRouter.setDynamicPenalty(dynamicPenalty);
        }

        progressBar = m_activity.findViewById(R.id.progressBar);
        calculatingTextView = m_activity.findViewById(R.id.calculatingTextView);

        coreRouter.calculateRoute(hereRouter.getRoutePlan(), new Router.Listener<List<RouteResult>, RoutingError>() {
            @Override
            public void onProgress(int i) {
                if (i < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    calculatingTextView.setVisibility(View.VISIBLE);
                    progressBar.setProgress(i);
                } else {
                    calculatingTextView.setVisibility(View.INVISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onCalculateRouteFinished(List<RouteResult> routeResults, RoutingError routingError) {
                if (routingError == RoutingError.NONE) {
                    if (routeResults.get(0).getRoute() != null) {
                        m_navigationManager = NavigationManager.getInstance();
                        isRouteOverView = true;
                        m_route = routeResults.get(0).getRoute();
                        initGuidanceManeuverView(m_route);
                        resetMapRoute(m_route);
                        mapRouteBBox = m_route.getBoundingBox();
                        if (mapRouteBBox.getHeight() > 0.01 || mapRouteBBox.getWidth() > 0.01) {
                            mapRouteBBox.expand(3000f, 1500f);
                        } else {
                            mapRouteBBox.expand(1500f, 750f);
                        }
                        m_map.zoomTo(mapRouteBBox, Map.Animation.LINEAR, Map.MOVE_PRESERVE_ORIENTATION);
                        m_naviControlButton.setText("Start Navi");
                        Toast.makeText(m_activity, "Length: " + m_route.getLength() + "m", Toast.LENGTH_LONG).show();
                    } else {
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(m_activity);
                        alertDialogBuilder.setTitle("Can't find a route.");
                        AlertDialog alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                    }
                } else {
                    calculatingTextView.setVisibility(View.INVISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                    retryRouting(m_activity, routingError, routeOptions);
                }
            }
        });
    }

    private void addNavigationListeners() {
        m_activity.findViewById(R.id.mapFragmentView).getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
        m_navigationManager.addNavigationManagerEventListener(new WeakReference<>(m_navigationManagerEventListener));
        m_navigationManager.addLaneInformationListener(new WeakReference<>(m_LaneInformationListener));
        m_navigationManager.addNewInstructionEventListener(new WeakReference<>(m_newInstructionEventListener));
        m_navigationManager.addSafetySpotListener(new WeakReference<>(safetySpotListener));
        m_navigationManager.setRealisticViewMode(NavigationManager.RealisticViewMode.DAY);
        m_navigationManager.addRealisticViewAspectRatio(NavigationManager.AspectRatio.AR_16x9);
        m_navigationManager.addRealisticViewListener(new WeakReference<>(m_realisticViewListener));
        m_navigationManager.addPositionListener(new WeakReference<>(m_positionListener));
        m_navigationManager.addRerouteListener(new WeakReference<>(new NavigationManager.RerouteListener() {
            @Override
            public void onRerouteBegin() {
                super.onRerouteBegin();
            }

            @Override
            public void onRerouteEnd(RouteResult routeResult, RoutingError routingError) {
                super.onRerouteEnd(routeResult, routingError);
                resetMapRoute(routeResult.getRoute());
            }
        }));
        m_navigationManager.addTrafficRerouteListener(new WeakReference<>(new NavigationManager.TrafficRerouteListener() {
            @Override
            public void onTrafficRerouted(RouteResult routeResult) {
                super.onTrafficRerouted(routeResult);
                resetMapRoute(routeResult.getRoute());
            }

            @Override
            public void onTrafficRerouteFailed(TrafficNotification trafficNotification) {
                super.onTrafficRerouteFailed(trafficNotification);
            }

            @Override
            public void onTrafficRerouteBegin(TrafficNotification trafficNotification) {
                super.onTrafficRerouteBegin(trafficNotification);
            }

            @Override
            public void onTrafficRerouteState(TrafficEnabledRoutingState trafficEnabledRoutingState) {
                super.onTrafficRerouteState(trafficEnabledRoutingState);
            }
        }));

    }

    void onDestroy() {
        /* Stop the navigation when app is destroyed */
        if (m_navigationManager != null) {
            stopForegroundService();
            m_navigationManager.stop();
        }
    }


}