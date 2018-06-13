package com.example.alexa.smarttransport;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.images.ImageRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, RoutingListener {

    private GoogleMap mMap;
    private Button mLogout, mSettings, mRideStatus;
    private int status = 0;
    private String destination;
    private LatLng destinationLatLng;
    private GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    private LocationRequest mLocationRequest;
    private SupportMapFragment mapFragment;
    private boolean logut = false;
    private String customerId = "";
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;
    private LatLng driverLatLng;
    private DatabaseReference ref;
    private Marker pickUpMarker;
    private LinearLayout mCustoemrInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName, mCustomerPhone, mCustomerDestination;
    private String user_id;
    String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    private Switch mWorkingSwitch;
    private LatLng pickUpLatLng;
    private float rideDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        polylines = new ArrayList<>();
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            mapFragment.getMapAsync(this);
        }
        mCustoemrInfo = findViewById(R.id.customerInfo);
        mCustomerProfileImage = findViewById(R.id.customerProfileImage);
        mCustomerName = findViewById(R.id.customerName);
        mCustomerPhone = findViewById(R.id.customerPhone);
        mCustomerDestination = findViewById(R.id.customerDestination);
        mWorkingSwitch = findViewById(R.id.workingSwitch);
        mWorkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectDriver();
                }
                else{
                    disconnectDriver();
                }
            }
        });
        mSettings = findViewById(R.id.settingsDriver);

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        mRideStatus = findViewById(R.id.rideStatus);
        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (status) {
                    //drviver it's on his way to pick up the customer
                    case 1:
                        status = 2;
                        erasePolylines();
                        if(destinationLatLng.latitude!=0.0 && destinationLatLng.longitude!=0.0){
                            getRouteToMarker(destinationLatLng);
                            mRideStatus.setText("Drive Completed");
                        }
                        break;
                    //driver it's on his way to destionation with the custoemr in his car
                    case 2:
                        recordRide();
                        endRide();
                        break;
                }
            }
        });


        mLogout = findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ref = FirebaseDatabase.getInstance().getReference("driversAvailable");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.removeLocation(FirebaseAuth.getInstance().getCurrentUser().getUid());

                DatabaseReference current_user_db = FirebaseDatabase.getInstance().getReference().child("customerRequest");
                current_user_db.removeValue();
                DatabaseReference current_user = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId);
                current_user.removeValue();

                if (customerId != null) {
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
                    driverRef.setValue(true);
                    customerId = null;
                }


                logut = true;
                FirebaseAuth.getInstance().signOut();

                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                //return;
            }
        });
        getAssignedCustomer();

    }

    private void getAssignedCustomer() {

        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    status = 1;
                    customerId = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickUpLocation();
                    getAssignedCustomerInfo();
                    getAssignedCustomerDestination();
                } else {
                    endRide();

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                //not needed
            }
        });
    }

    //get the location from the driver and set a marker on that location
    private void getAssignedCustomerPickUpLocation() {
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && !customerId.equals("")) {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if (map.get(0) != null) { //make sure map data exist
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) { //make sure map data exist
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    //add marker to map pointing where the driver is
                    pickUpLatLng = new LatLng(locationLat, locationLng);
                    pickUpMarker = mMap.addMarker(new MarkerOptions().position(pickUpLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_location1)));
                    getRouteToMarker(pickUpLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                //not needed
            }
        });
    }

    private void getRouteToMarker(LatLng pickUpLatLng) {
        if(pickUpLatLng != null && mLastLocation != null) {
            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickUpLatLng)
                    .build();
            routing.execute();
        }
    }

    private void getAssignedCustomerInfo() {
        mCustoemrInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("name") != null) {
                        mCustomerName.setText(map.get("name").toString());
                    }
                    if (map.get("phone") != null) {
                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if (map.get("profileImageUrl") != null) {
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }
    private void getAssignedCustomerDestination() {
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("destination") != null) {
                        destination = map.get("destination").toString();
                        mCustomerDestination.setText("Destination" + destination);
                    } else {
                        mCustomerDestination.setText("Destination--");
                    }
                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;
                    if (map.get("destinationLat") != null) {
                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
                    }
                    if (map.get("destinationLng") != null) {
                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                        destinationLatLng = new LatLng(destinationLat, destinationLng);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                //not needed
            }
        });
    }
    private void endRide() {
        mRideStatus.setText("picked customer");
        erasePolylines();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("customerRequest");
        driverRef.removeValue();

        ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);
        customerId = "";
        rideDistance = 0;

        if (pickUpMarker != null) {
            pickUpMarker.remove();
        }
        if (assignedCustomerPickupLocationRef != null) {
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
        }
        mCustoemrInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhone.setText("");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_default_user);
        mCustomerDestination.setText("Destination: --");
    }
    private void recordRide(){
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");
        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        HashMap map = new HashMap();
        map.put("driver",userId);
        map.put("customer",customerId);
        map.put("rating",0);
        map.put("timeStamp",getCurrentTimeStamp());
        map.put("destination",destination);
        map.put("location/from/lat", pickUpLatLng.latitude);
        map.put("location/from/lng", pickUpLatLng.longitude);
        map.put("location/to/lat", destinationLatLng.latitude);
        map.put("location/to/lng", destinationLatLng.longitude);
        map.put("distance", rideDistance);
        historyRef.child(requestId).updateChildren(map);
        driverRef.removeValue();
    }

    private Long getCurrentTimeStamp() {
        Long timeStamp = System.currentTimeMillis()/1000;
        return timeStamp;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (getApplicationContext()!= null) {
            if (!customerId.equals("") && mLastLocation!=null && location !=null) {
                rideDistance += mLastLocation.distanceTo(location)/1000;
            }
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");

            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);
            if (customerId == "") {
                geoFireWorking.removeLocation(userId);
                geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
            } else {
                geoFireAvailable.removeLocation(userId);
                geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
            }
        }
    }
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //creating request every second
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }
    private void connectDriver(){
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }
    private void disconnectDriver(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }
    final int LOCATION_REQUEST_CODE = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapFragment.getMapAsync(this);
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }
    //we need to know when a driver is no longer available
    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    @Override
    public void onRoutingFailure(RouteException e) {
        if (e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingStart() {
    }
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if (polylines.size() > 0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }
        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i < route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(), "Route " + (i + 1) + ": distance - " + route.get(i).getDistanceValue() + ": duration - " + route.get(i).getDurationValue(), Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingCancelled() {
    }
    private void erasePolylines() {
        for (Polyline line : polylines) {
            line.remove();
        }
        polylines.clear();
    }
}

