package com.welch.golfbuddy;

import android.os.Bundle;
import android.view.Menu;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements LocationListener, OnMapLongClickListener, OnMarkerDragListener,
	GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener  {

	public static final double METERS_TO_YARDS = 1.09361;
	private static GoogleMap mMap;		
	private static MarkerOptions markerOptions;
	private static Marker centerMarker;
	private static Polyline polyline;
	private TextView distanceTextView;
	
	/*
     * Constants for location update parameters
     */
    // Milliseconds per second
    public static final int MILLISECONDS_PER_SECOND = 1000;

    // The update interval
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    // A fast interval ceiling
    public static final int FAST_CEILING_IN_SECONDS = 1;

    // Update interval in milliseconds
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    // A fast ceiling of update intervals, used when the app is visible
    public static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS =
            MILLISECONDS_PER_SECOND * FAST_CEILING_IN_SECONDS;
    
    // A request to connect to Location Services
    private LocationRequest mLocationRequest;

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;
    
    // The last location
    private Location lastLocation;
    
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Ensure that Google Play services is installed
		int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if(resp != ConnectionResult.SUCCESS){
			Toast.makeText(this, "Google Play Services needs to be installed", Toast.LENGTH_LONG).show();			
		}
		
		// Get a handle to the text view
		distanceTextView = (TextView) findViewById(R.id.distance);
		distanceTextView.setShadowLayer(5, 10, 10, Color.BLACK);
						
		// Get a handle to the map
		setUpMapIfNeeded();
				
		// Setup the app based on the user's shared preferences
		setupPreferences();
		
		// Create a new global location parameters object
        mLocationRequest = LocationRequest.create();

        /*
         * Set the update interval
         */
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Set the interval ceiling to one minute
        mLocationRequest.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Get a handle to the map
		setUpMapIfNeeded();

		// Enable the My Location layer on the map to show the device's location
		mMap.setMyLocationEnabled(true);
		
		// Redraw the marker since it does not persist
		if(centerMarker != null && markerOptions != null) {
			centerMarker.remove();
			centerMarker = mMap.addMarker(markerOptions);
		}			
	}
	
	@Override
    protected void onStart() {
		super.onStart();
		
		/*
         * Connect the client. Don't re-start any requests here;
         * instead, wait for onResume()
         */
        mLocationClient.connect();
    }
		
	@Override
	protected void onPause() {
		super.onPause();	
		
		// Disable the My Location layer on the map to show the device's location
		mMap.setMyLocationEnabled(false);
	}
	
	@Override
    public void onStop() {

        // If the client is connected
        if (mLocationClient.isConnected()) {
            stopPeriodicUpdates();
        }

        // After disconnect() is called, the client is considered "dead".
        mLocationClient.disconnect();

        super.onStop();
    }
		
	@Override
	protected void onDestroy() {
		super.onDestroy();		
					
		// Get the camera position
		LatLng lastLoc = mMap.getCameraPosition().target;
		float zoom = mMap.getCameraPosition().zoom;
		float bearing = mMap.getCameraPosition().bearing;
		float tilt = mMap.getCameraPosition().tilt;
		
		// Save the camera position so it can be restored when the application is restarted
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putFloat(getString(R.string.pref_key_latitude), (float) lastLoc.latitude);
		editor.putFloat(getString(R.string.pref_key_longitude), (float) lastLoc.longitude);
		editor.putFloat(getString(R.string.pref_key_zoom), zoom);
		editor.putFloat(getString(R.string.pref_key_bearing), bearing);
		editor.putFloat(getString(R.string.pref_key_tilt), tilt);
		editor.commit();
		
		// Important to set the map to null
		mMap = null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	/**
	 * Show a dialog to the user requesting that GPS be enabled
	 */
	private void showDialogGPS() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		builder.setTitle(getString(R.string.gps_title));
		builder.setMessage(getString(R.string.gps_message));
		builder.setInverseBackgroundForced(true);
		builder.setPositiveButton(getString(R.string.gps_enable_button), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				startActivity(
						new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
			}
		});
		builder.setNegativeButton(getString(R.string.gps_ignore_button), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();

				Toast.makeText(getApplicationContext(),
						getString(R.string.gps_toast), Toast.LENGTH_SHORT).show();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Instantiate the map if needed
	 */
	private void setUpMapIfNeeded() {		
		// Do a null check to confirm that we have not already instantiated the map
		if (mMap == null) {			
			// Make sure that GPS is enabled on the device
			LocationManager mlocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);;
		    boolean enabled = mlocManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		    		    
		    if(!enabled) {
		    	showDialogGPS();
		    }
		    
			mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
			
			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				// The Map is verified. It is now safe to manipulate the map.
				setUpMap();
			}
		}
	}

	/**
	 * Setup the map event listeners
	 */
	private void setUpMap() {
		// Register the map listeners
		mMap.setOnMapLongClickListener(this);
		mMap.setOnMarkerDragListener(this);
	}
	
	/**
	 * Restore the user's shared preferences
	 */
	private void setupPreferences() {		
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
						
		// Animate camera position to last know location
		float lat = sharedPreferences.getFloat(getString(R.string.pref_key_latitude), 0f);
		float lon = sharedPreferences.getFloat(getString(R.string.pref_key_longitude), 0f);		
		float bearing = sharedPreferences.getFloat(getString(R.string.pref_key_bearing), 0f);
		float tilt = sharedPreferences.getFloat(getString(R.string.pref_key_tilt), 30f);
		float zoomAmount = sharedPreferences.getFloat(getString(R.string.pref_key_zoom), 13f);
		
		mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
		mMap.getUiSettings().setCompassEnabled(true);
		mMap.getUiSettings().setZoomControlsEnabled(true);
		
		// If the latitude and longitude are both zero than use the default position
		if(lat != 0f && lon != 0f) {		
			CameraPosition cameraPosition = new CameraPosition.Builder()
				.target(new LatLng(lat, lon)) 	// Sets the center of the map
				.zoom(zoomAmount)               // Sets the zoom
				.bearing(bearing)               // Sets the orientation of the camera
				.tilt(tilt)                   	// Sets the tilt of the camera in degrees
				.build();                   	// Creates a CameraPosition from the builder
			
			mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
		}
		else {
			CameraPosition cameraPosition = new CameraPosition.Builder()
				.target(new LatLng(39.130959, -94.581985)) 	// Sets the center of the map
				.zoom(3)               			// Sets the zoom
				.tilt(30)                   	// Sets the tilt of the camera in degrees
				.build();                   	// Creates a CameraPosition from the builder
		
			mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
		}
	}

	/**
	 * Get the length of the circle's radius in meters
	 * @param center the center of the circle
	 * @param radius a point on the circumference of the circle
	 * @return the length of the radius in meters
	 */
	private static int toRadiusYards(LatLng center, LatLng radius) {		
		float[] result = new float[1];
		Location.distanceBetween(center.latitude, center.longitude,
				radius.latitude, radius.longitude, result);
		
		int yards = (int) Math.round(result[0] * METERS_TO_YARDS);
		
		return yards;
	}

	@Override
	public void onMapLongClick(LatLng point) {			
		// Remove the old marker from the map
		if(centerMarker != null) {
			centerMarker.remove();
		}
		
		markerOptions = new MarkerOptions()
				.position(point)
				.draggable(true)
				.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
				
		// Add the new marker to the map
		centerMarker = mMap.addMarker(markerOptions);
		
		if(lastLocation != null) {
			updateYardage(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), point);
		}        
	}
	
	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		
	}

	@Override
	public void onConnected(Bundle arg0) {		
		startPeriodicUpdates();
	}

	@Override
	public void onDisconnected() {
		
	}
	
	// Define the callback method that receives location updates
    @Override
    public void onLocationChanged(Location location) {
        
    	lastLocation = location;
        
        if(centerMarker != null && polyline != null) {
        	updateYardage(new LatLng(location.getLatitude(), location.getLongitude()), centerMarker.getPosition());
        }
    }
    
    private void updateYardage(LatLng source, LatLng destination) {    	
    	int distance = toRadiusYards(source, destination);
    	distanceTextView.setText(distance + " yds");
    	
    	// Remove the old Polyline
    	if(polyline != null) {
    		polyline.remove();
    	}
    	// Instantiates a new Polyline object and adds points to define a rectangle
    	PolylineOptions lineOptions = new PolylineOptions()
    			.color(0xff33b5e5)
    			.width(7)
    			.add(source)
    			.add(destination);
    	// Get back the mutable Polyline
    	polyline = mMap.addPolyline(lineOptions);
    }
	
	/**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private void startPeriodicUpdates() {
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private void stopPeriodicUpdates() {
        mLocationClient.removeLocationUpdates(this);
    }

	@Override
	public void onMarkerDrag(Marker arg0) {
		if(lastLocation != null) {
			updateYardage(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), arg0.getPosition());
		}		
	}

	@Override
	public void onMarkerDragEnd(Marker arg0) {
		
	}

	@Override
	public void onMarkerDragStart(Marker arg0) {
		
	}
}
