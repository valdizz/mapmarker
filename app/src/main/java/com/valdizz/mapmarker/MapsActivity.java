package com.valdizz.mapmarker;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnCameraMoveStartedListener {

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private boolean mLocationPermissionGranted;
    private AddressResultReceiver mResultReceiver;
    private CameraPosition mCameraPosition;
    private Location mLastLocation;
    @BindView(R.id.image_location) ImageView image_location;
    @BindView(R.id.tv_address) TextView tv_address;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        ButterKnife.bind(this);
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mResultReceiver = new AddressResultReceiver(new Handler());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    // Manipulates the map when it's available. This callback is triggered when the map is ready to be used.
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setOnCameraIdleListener(this);
        mMap.setOnCameraMoveStartedListener(this);

        getLocationPermission();
        updateLocationUI();
        getDeviceLocation();
    }

    // Save current state.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(Constants.LAST_LOCATION_KEY, mLastLocation);
        outState.putParcelable(Constants.CAMERA_POSITION_KEY, mCameraPosition);
    }

    // Restore last state.
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLastLocation = savedInstanceState.getParcelable(Constants.LAST_LOCATION_KEY);
        mCameraPosition = savedInstanceState.getParcelable(Constants.CAMERA_POSITION_KEY);
    }

    // Gets the current location of the device, and positions the map's camera.
    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastLocation = task.getResult();
                            fetchCurrentAddress();
                            if (mCameraPosition == null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), Constants.DEFAULT_ZOOM));
                            }
                            else {
                                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
                            }
                            image_location.setVisibility(View.VISIBLE);
                        } else {
                            Log.e(Constants.TAG, "Location exception: %s", task.getException());
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    // Prompts the user for permission to use the device location.
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),  android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    // Handles the result of the request for location permissions.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    // Updates the map's UI settings based on whether the user has granted location permission.
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    // Show the current place on the map - provided the user has granted location permission.
    private void showCurrentAddress(String address) {
        if (mMap == null) {
            return;
        }
        if (mLocationPermissionGranted) {
            tv_address.setVisibility(View.VISIBLE);
            tv_address.setText(address);
        } else {
            Log.i(Constants.TAG, "The user did not grant location permission.");
            getLocationPermission();
        }
    }

    // Is invoked when the camera stops moving and the user has stopped interacting with the map.
    @Override
    public void onCameraIdle() {
        if (mLastLocation != null) {
            mCameraPosition = mMap.getCameraPosition();
            mLastLocation.setLatitude(mCameraPosition.target.latitude);
            mLastLocation.setLongitude(mCameraPosition.target.longitude);
            fetchCurrentAddress();
        }
    }

    @Override
    public void onCameraMoveStarted(int i) {
        tv_address.setVisibility(View.GONE);
        tv_address.setText("");
    }

    // Start service when the user takes an action that requires a geocoding address lookup.
    private void fetchCurrentAddress() {
        if (mLastLocation == null) {
            return;
        }
        if (!Geocoder.isPresent()) {
            Toast.makeText(MapsActivity.this, R.string.no_geocoder_available, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        startService(intent);
    }

    // After the intent service handles the geocoding request, it uses a ResultReceiver to return the results to the activity that made the request.
    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        // Override the onReceiveResult() method to handle the results (address) that are delivered to the result receiver.
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultData == null) {
                return;
            }
            String mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            if (mAddressOutput == null) {
                mAddressOutput = "";
            }
            showCurrentAddress(mAddressOutput);
        }
    }
}
