package ca.ubc.cpsc210.waldo.map;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.SimpleLocationOverlay;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path.Direction;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import ca.ubc.cpsc210.waldo.R;
import ca.ubc.cpsc210.waldo.exceptions.WaldoException;
import ca.ubc.cpsc210.waldo.model.Bus;
import ca.ubc.cpsc210.waldo.model.BusRoute;
import ca.ubc.cpsc210.waldo.model.BusStop;
import ca.ubc.cpsc210.waldo.model.Trip;
import ca.ubc.cpsc210.waldo.model.Waldo;
import ca.ubc.cpsc210.waldo.translink.TranslinkService;
import ca.ubc.cpsc210.waldo.util.LatLon;
import ca.ubc.cpsc210.waldo.util.Segment;
import ca.ubc.cpsc210.waldo.waldowebservice.WaldoService;

/**
 * Fragment holding the map in the UI.
 * 
 * @author CPSC 210 Instructor
 */
public class MapDisplayFragment extends Fragment {

	/**
	 * Log tag for LogCat messages
	 */
	private final static String LOG_TAG = "MapDisplayFragment";

	/**
	 * Location of some points in lat/lon for testing and for centering the map
	 */
	private final static GeoPoint ICICS = new GeoPoint(49.261182, -123.2488201);
	private final static GeoPoint CENTERMAP = ICICS;

	/**
	 * Preference manager to access user preferences
	 */
	private SharedPreferences sharedPreferences;

	/**
	 * View that shows the map
	 */
	private MapView mapView;

	/**
	 * Map controller for zooming in/out, centering
	 */
	private MapController mapController;

	// **************** Overlay fields **********************

	/**
	 * Overlay for the device user's current location.
	 */
	private SimpleLocationOverlay userLocationOverlay;

	/**
	 * Overlay for bus stop to board at
	 */
	private ItemizedIconOverlay<OverlayItem> busStopToBoardOverlay;

	/**
	 * Overlay for bus stop to disembark
	 */
	private ItemizedIconOverlay<OverlayItem> busStopToDisembarkOverlay;

	/**
	 * Overlay for Waldo
	 */
	private ItemizedIconOverlay<OverlayItem> waldosOverlay;

	/**
	 * Overlay for displaying bus routes
	 */
	private List<PathOverlay> routeOverlays;

	/**
	 * Selected bus stop on map
	 */
	private OverlayItem selectedStopOnMap;

	/**
	 * Bus selected by user
	 */
	private OverlayItem selectedBus;

	// ******************* Application-specific *****************

	/**
	 * Wraps Translink web service
	 */
	private TranslinkService translinkService;

	/**
	 * Wraps Waldo web service
	 */
	private WaldoService waldoService;

	/**
	 * Waldo selected by user
	 */
	private Waldo selectedWaldo;

	/*
	 * The name the user goes by
	 */
	private String userName;

	//private Location userLocation;

	LocationManager locationManager;

	MyLocationListener locListen; 

	// ***************** Android hooks *********************

	/**
	 * Help initialize the state of the fragment
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(getActivity());

		waldoService = new WaldoService();
		translinkService = new TranslinkService();
		routeOverlays = new ArrayList<PathOverlay>();


		initializeWaldo();

		locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
		locListen = new MyLocationListener();

		Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, locListen);


		if(location != null) {

			locListen.onLocationChanged(location);
		}


	}

	/**
	 * Initialize the Waldo web service
	 */
	private void initializeWaldo() {
		String s = null;
		new InitWaldo().execute(s);
	}

	/**
	 * Set up map view with overlays for buses, selected bus stop, bus route and
	 * current location.
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		if (mapView == null) {
			mapView = new MapView(getActivity(), null);

			mapView.setTileSource(TileSourceFactory.MAPNIK);
			mapView.setClickable(true);
			mapView.setBuiltInZoomControls(true);

			mapController = mapView.getController();
			mapController.setZoom(mapView.getMaxZoomLevel() - 4);
			mapController.setCenter(CENTERMAP);

			userLocationOverlay = createLocationOverlay();
			busStopToBoardOverlay = createBusStopToBoardOverlay();
			busStopToDisembarkOverlay = createBusStopToDisembarkOverlay();
			waldosOverlay = createWaldosOverlay();

			// Order matters: overlays added later are displayed on top of
			// overlays added earlier.
			mapView.getOverlays().add(waldosOverlay);
			mapView.getOverlays().add(busStopToBoardOverlay);
			mapView.getOverlays().add(busStopToDisembarkOverlay);
			mapView.getOverlays().add(userLocationOverlay);
		}

		return mapView;
	}

	/**
	 * Helper to reset overlays
	 */
	private void resetOverlays() {
		OverlayManager om = mapView.getOverlayManager();
		om.clear();
		om.addAll(routeOverlays);
		om.add(busStopToBoardOverlay);
		om.add(busStopToDisembarkOverlay);
		om.add(userLocationOverlay);
		om.add(waldosOverlay);
	}

	/**
	 * Helper to clear overlays
	 */
	private void clearOverlays() {
		waldosOverlay.removeAllItems();
		clearAllOverlaysButWaldo();
		OverlayManager om = mapView.getOverlayManager();
		om.add(waldosOverlay);
	}

	/**
	 * Helper to clear overlays, but leave Waldo overlay untouched
	 */
	private void clearAllOverlaysButWaldo() {
		if (routeOverlays != null) {
			routeOverlays.clear();
			busStopToBoardOverlay.removeAllItems();
			busStopToDisembarkOverlay.removeAllItems();

			OverlayManager om = mapView.getOverlayManager();
			om.clear();
			om.addAll(routeOverlays);
			om.add(busStopToBoardOverlay);
			om.add(busStopToDisembarkOverlay);
			om.add(userLocationOverlay);
		}
	}

	/**
	 * When view is destroyed, remove map view from its parent so that it can be
	 * added again when view is re-created.
	 */
	@Override
	public void onDestroyView() {
		((ViewGroup) mapView.getParent()).removeView(mapView);
		super.onDestroyView();
	}

	/**
	 * Shut down the various services
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	/**
	 * Update the overlay with user's current location. Request location
	 * updates.
	 */
	@Override
	public void onResume() {

		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, locListen);

		// CPSC 210 students, you'll need to handle parts of location updates
		// here...

		initializeWaldo();

		super.onResume();
	}

	/**
	 * Cancel location updates.
	 */
	@Override
	public void onPause() {
		// CPSC 210 students, you'll need to do some work with location updates
		// here...
		locationManager.removeUpdates(locListen);

		super.onPause();
	}

	/**
	 * Update the marker for the user's location and repaint.
	 */
	public void updateLocation(Location location) {
		
		// CPSC 210 Students: Implement this method. mapView.invalidate is
		// needed to redraw
		// the map and should come at the end of the method.
		
		
		System.out.println("Updaaaate Location!!!!");

		GeoPoint newLoc = new GeoPoint(location.getLatitude(), location.getLongitude());

		userLocationOverlay.setLocation(newLoc);

		mapView.invalidate();
	}

	/**
	 * Save map's zoom level and centre.
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mapView != null) {
			outState.putInt("zoomLevel", mapView.getZoomLevel());
			IGeoPoint cntr = mapView.getMapCenter();
			outState.putInt("latE6", cntr.getLatitudeE6());
			outState.putInt("lonE6", cntr.getLongitudeE6());
		}
	}

	/**
	 * Retrieve Waldos from the Waldo web service
	 */
	public void findWaldos() {
		clearOverlays();
		// Find out from the settings how many waldos to retrieve, default is 1
		String numberOfWaldosAsString = sharedPreferences.getString(
				"numberOfWaldos", "1");
		int numberOfWaldos = Integer.valueOf(numberOfWaldosAsString);
		new GetWaldoLocations().execute(numberOfWaldos);
		mapView.invalidate();
	}

	/**
	 * Clear waldos from view
	 */
	public void clearWaldos() {
		clearOverlays();
		mapView.invalidate();

	}

	// ******************** Overlay Creation ********************

	/**
	 * Create the overlay for bus stop to board at marker.
	 */
	private ItemizedIconOverlay<OverlayItem> createBusStopToBoardOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display bus stop description in dialog box when user taps stop.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						if (selectedStopOnMap != null) {
							selectedStopOnMap.setMarker(getResources()
									.getDrawable(R.drawable.pin_blue));

							mapView.invalidate();
						}
					}
				}).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
				.show();

				oi.setMarker(getResources().getDrawable(R.drawable.pin_blue));
				selectedStopOnMap = oi;
				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.pin_blue), gestureListener, rp);
	}

	/**
	 * Create the overlay for bus stop to disembark at marker.
	 */
	private ItemizedIconOverlay<OverlayItem> createBusStopToDisembarkOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display bus stop description in dialog box when user taps stop.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						if (selectedStopOnMap != null) {
							selectedStopOnMap.setMarker(getResources()
									.getDrawable(R.drawable.pin_blue));

							mapView.invalidate();
						}
					}
				}).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
				.show();

				oi.setMarker(getResources().getDrawable(R.drawable.pin_blue));
				selectedStopOnMap = oi;
				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.pin_blue), gestureListener, rp);
	}

	/**
	 * Create the overlay for Waldo markers.
	 */
	private ItemizedIconOverlay<OverlayItem> createWaldosOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());
		OnItemGestureListener<OverlayItem> gestureListener = new OnItemGestureListener<OverlayItem>() {

			/**
			 * Display Waldo point description in dialog box when user taps
			 * icon.
			 * 
			 * @param index
			 *            index of item tapped
			 * @param oi
			 *            the OverlayItem that was tapped
			 * @return true to indicate that tap event has been handled
			 */
			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem oi) {

				selectedWaldo = waldoService.getWaldos().get(index);
				Date lastSeen = selectedWaldo.getLastUpdated();
				SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
						"MMM dd, hh:mmaa", Locale.CANADA);

				new AlertDialog.Builder(getActivity())
				.setPositiveButton(R.string.get_route,
						new OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0,
							int arg1) {

						// CPSC 210 STUDENTS. You must set
						// currCoord to
						// the user's current location.
						Criteria c = new Criteria();
						String provider = locationManager.getBestProvider(c, true);
//49.26612,-123.24703	
						//double userLat = userLocation.getLatitude();
						//double userLong = userLocation.getLongitude();
						
						
						LatLon currCoord = new LatLon(49.26612,-123.24703);

						// CPSC 210 Students: Set currCoord...

						LatLon destCoord = selectedWaldo
								.getLastLocation();

						new GetRouteTask().execute(currCoord,
								destCoord);

					}
				})
				.setNegativeButton(R.string.ok, null)
				.setTitle(selectedWaldo.getName())
				.setMessage(
						"Last seen  " + dateTimeFormat.format(lastSeen))
						.show();

				mapView.invalidate();
				return true;
			}

			@Override
			public boolean onItemLongPress(int index, OverlayItem oi) {
				// do nothing
				return false;
			}
		};

		return new ItemizedIconOverlay<OverlayItem>(
				new ArrayList<OverlayItem>(), getResources().getDrawable(
						R.drawable.map_pin_thumb_blue), gestureListener, rp);
	}

	/**
	 * Create overlay for a bus route.
	 */
	private PathOverlay createPathOverlay() {
		PathOverlay po = new PathOverlay(Color.parseColor("#cf0c7f"),
				getActivity());
		Paint pathPaint = new Paint();
		pathPaint.setColor(Color.parseColor("#cf0c7f"));
		pathPaint.setStrokeWidth(4.0f);
		pathPaint.setStyle(Style.STROKE);
		po.setPaint(pathPaint);
		return po;
	}

	/**
	 * Create the overlay for the user's current location.
	 */
	private SimpleLocationOverlay createLocationOverlay() {
		ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

		return new SimpleLocationOverlay(getActivity(), rp) {
			@Override
			public boolean onLongPress(MotionEvent e, MapView mapView) {
				new GetMessagesFromWaldo().execute();
				return true;
			}

		};
	}

	/**
	 * Plot endpoints
	 */
	private void plotEndPoints(Trip trip) {

		String string = new String();  
		String string1 = new String();
		int waitingTime = 0;

		for(Bus b: trip.getRoute().getBuses()) {
			//                if(waitingTime > b.getMinutesToDeparture()) {
			//                        smallestWait = ccc.getMinutesToDeparture();
			//                }
			waitingTime = b.getMinutesToDeparture();
		}
		string = "Next" + trip.getRoute().getRouteNumber() + "Arriving In: " + waitingTime;
		string1 = "Here is your destination";

		trip.getStart().setDescriptionToDisplay(string);
		trip.getEnd().setDescriptionToDisplay(string1);
		/////////////////////////////////////////////////////////////////        
		GeoPoint pointStart = new GeoPoint(trip.getStart().getLatLon()
				.getLatitude(), trip.getStart().getLatLon().getLongitude());

		OverlayItem overlayItemStart = new OverlayItem(Integer.valueOf(
				trip.getStart().getNumber()).toString(), trip.getStart()
				.getDescriptionToDisplay(), pointStart);
		GeoPoint pointEnd = new GeoPoint(trip.getEnd().getLatLon()
				.getLatitude(), trip.getEnd().getLatLon().getLongitude());
		OverlayItem overlayItemEnd = new OverlayItem(Integer.valueOf(
				trip.getEnd().getNumber()).toString(), trip.getEnd()
				.getDescriptionToDisplay(), pointEnd);
		busStopToBoardOverlay.removeAllItems();
		busStopToDisembarkOverlay.removeAllItems();

		busStopToBoardOverlay.addItem(overlayItemStart);
		busStopToDisembarkOverlay.addItem(overlayItemEnd);
	}

	/**
	 * Plot bus route onto route overlays
	 * 
	 * @param rte
	 *            : the bus route
	 * @param start
	 *            : location where the trip starts
	 * @param end
	 *            : location where the trip ends
	 */
	private void plotRoute(Trip trip) {

		// Put up the end points
		plotEndPoints(trip);
		// CPSC 210 STUDENTS: Complete the implementation of this method

		System.out.println("plotting triiiiiiips!!!");

		List<Segment> allSegments = trip.getRoute().getSegments();
		//translinkService.parseKMZ(trip.getRoute());
		System.out.println("The sseeegments arrreee what size?: " + trip.getRoute().getSegments().size()  
				+ " and in stringss?___________," + trip.getRoute().getSegments().toString());

		System.out.println("My trip is: " + "__" + trip.toString() + "__" + trip.getRoute().toString() );
		System.out.println("My segments are empty?" + trip.getRoute().getSegments().isEmpty());

		System.out.println("After ploooooTing : " + trip.getRoute().getRouteNumber());

		for (Segment segment: allSegments) {
			PathOverlay myPathOverlay = createPathOverlay();

			System.out.println("Printing overlaaaaayyyssss!!!" + myPathOverlay.isEnabled() + myPathOverlay.getNumberOfPoints());
			for (LatLon point: segment) {

				System.out.println("The point is :... " + point.toString());
				System.out.println("plotting triiiiiiips22222222!!!");
				if (LatLon.inbetween(point, trip.getStart().getLatLon(), trip.getEnd().getLatLon())) {

					System.out.println("the trip name is: " + trip.getStart().getName());
					GeoPoint gp = new GeoPoint(point.getLatitude(), point.getLongitude());

					myPathOverlay.addPoint(gp);
				}
			}
			routeOverlays.add(myPathOverlay);
		}

		resetOverlays();

		// This should be the last method call in this method to redraw the map
		mapView.invalidate();
	}

	/**
	 * Plot a Waldo point on the specified overlay.
	 */
	private void plotWaldos(List<Waldo> waldos) {

		for(Waldo waldo : waldos) {

			GeoPoint gp = new GeoPoint(waldo.getLastLocation().getLatitude(), 
					waldo.getLastLocation().getLongitude());

			OverlayItem overlay = new OverlayItem("string", waldo.getName(), gp);

			List<OverlayItem> overlayList = new ArrayList<OverlayItem>();
			overlayList.add(overlay);

			waldosOverlay.addItems(overlayList);

			// now, items contains one OverlayItem, but also 11 empty (null) Items


		}
		// CPSC 210 STUDENTS: Complete the implementation of this method

		// This should be the last method call in this method to redraw the map
		mapView.invalidate();
	}



	public class MyLocationListener implements LocationListener{

		@Override
		public void onLocationChanged(Location loc) {

			double lat = loc.getLatitude();
			double lng = loc.getLongitude();
			//userLocation = location;
			updateLocation(loc);

		}


		@Override
		public void onProviderDisabled(String arg0) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onProviderEnabled(String arg0) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			// TODO Auto-generated method stub
		}
	}





	/**
	 * Helper to create simple alert dialog to display message
	 * 
	 * @param msg
	 *            message to display in alert dialog
	 * @return the alert dialog
	 */
	private AlertDialog createSimpleDialog(String msg) {
		AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
		dialogBldr.setMessage(msg);
		dialogBldr.setNeutralButton(R.string.ok, null);
		return dialogBldr.create();
	}

	/**
	 * Asynchronous task to get a route between two endpoints. Displays progress
	 * dialog while running in background.
	 */
	private class GetRouteTask extends AsyncTask<LatLon, Void, Trip> {
		private ProgressDialog dialog = new ProgressDialog(getActivity());
		private LatLon startPoint;
		private LatLon endPoint;

		@Override
		protected void onPreExecute() {
			translinkService.clearModel();
			dialog.setMessage("Retrieving route...");
			dialog.show();
		}

		@Override
		protected Trip doInBackground(LatLon... routeEndPoints) {

			/**
			 * Make A new trip object that can be passed on to plotRoute and plotEndPoints
			 * The statement below checks this method has been called.
			 */

			System.out.println("I am making a Trip!!!!");
			Trip trip = null;

			// THe start and end point for the route
			startPoint = routeEndPoints[0];
			endPoint = routeEndPoints[1];

			// CPSC 210 Students: Complete this method. It must return a trip.

			Set<BusStop> busAtStart = new HashSet<BusStop>();
			Set<BusStop> busAtEnd = new HashSet<BusStop>();

			String distance = sharedPreferences.getString("stopDistance", "500");
			int dist = Integer.parseInt(distance);

			/**
			 * Using translinkService.getBusStopsAround we use the startPoint latlon and the distance string that we have converted
			 * into an integer to get a list of busStops at the beginning and at the end.
			 */

			busAtStart = translinkService.getBusStopsAround(startPoint, dist);
			busAtEnd = translinkService.getBusStopsAround(endPoint, dist);

			/**
			 * We use these busStops to pass hem into this walkDistance method that checks whether the BusStops at the end are whithin 
			 * walking Distance of each other. If so then we create a nuew trip object with most fields null.
			 */
			if(walkDistance(busAtStart, busAtEnd)) {

				trip = new Trip(null, null, null, null, true);
				return trip;

			}

			Set<BusRoute> routesL = new HashSet<BusRoute>();

			Set<BusRoute> translinkStartRoutes = new HashSet<BusRoute>();
			Set<BusRoute> translinkEndRoutes = new HashSet<BusRoute>();

			/**
			 * In order for us to make some routes for these busStops we need to go through each one individually to not only get the route 
			 * but also we need to know if they are available in translink.  we add all startroutes and endroutes to  this to translinkStartRoutes 
			 * and translinkEndroutes respectively. 
			 * 
			 */
			for(BusStop b : busAtStart) {
				for(BusRoute r : b.getRoutes()) {

					if(translinkService.lookupRoute(r.getRouteNumber()) != null) {
						translinkStartRoutes.add(r);
					}
				}	
			}
			for(BusStop b : busAtEnd) {
				for(BusRoute r : b.getRoutes()) {

					if(translinkService.lookupRoute(r.getRouteNumber()) != null) {
						translinkEndRoutes.add(r);
					}
				}
			}

			/**
			 * commBusRoutes calls getRoutes() for each busStop at the start and at the end. we then call translinkEndRoutes and translinkEndRoutes to see 
			 * if they exist in routesL. If so it is not removed from routesL. The checks below this for loop are used to check if the routes may be empty in 
			 * any one of these lists. 
			 */		
			routesL = commBusRoutes(busAtStart, busAtEnd);

			for(BusRoute r : routesL) {

				if((!translinkStartRoutes.contains(r)) && !(translinkEndRoutes.contains(r)) ) {
					routesL.remove(r);
				}
			}

			System.out.println("IT IS " + translinkStartRoutes.isEmpty() + "translinkStartRoutes THAT THE ROUTES ARE EMPTY");
			System.out.println("IT IS " + translinkEndRoutes.isEmpty() + "translinkEndRoutes THAT THE ROUTES ARE EMPTY");

			System.out.println("are my routes empty?"+ "=" + routesL.isEmpty());


			BusStop closbStop = null;
			BusStop closeStop = null;

			BusRoute routeToTake = null;

			String corrDirection;

			String routingType = sharedPreferences.getString("routingOptions", "closest_stop_me");
			String routingType2 = sharedPreferences.getString("routingOptions", "closest_dest_me");
			
			
			/**
			 * Now that routesL contains only the routes that are in translink we can see if both of these lists of BusStops exist in both
			 * of them. If this is so, we take only the fist one that does by calling the break call at the bottom inside the third for loop.
			 * This only occurs when routingType is closest_stop_me. if not then we are dealing with the case when routing type is closest_stop_dest.
			 * 
			 * In the second case we take the position where we must get the busStop closest to the start. We go through the loop until we know all
			 * our routes from the waldo can give us a BusStop that is closest to the start point by eliminating them every time we enter the while loop. 
			 * return. The statements below this are merely checks what route comes out from all our loops.
			 */
			
			if(routingType.equals("closest_stop_me")) {
				for(BusStop s: busAtStart) {
					for(BusStop e: busAtEnd) {
						for(BusRoute r: routesL) {

							if(s.getRoutes().contains(r) && e.getRoutes().contains(r)) {

								closbStop = s;
								closeStop = e;
								routeToTake = r;

								corrDirection = toDirection(s, endPoint);
								break;
							}
						}
					}
				}

			}else{

				while(busAtStart.isEmpty() == false) {

					BusStop s = closeBusStop(startPoint, busAtStart);
					
					if(routingType2.equals("closest_stop_dest"))
					for(BusStop e: busAtEnd) {
						for(BusRoute r: routesL) {
							
							System.out.println("Is my routesL empty: " + routesL.isEmpty());
							if(s.getRoutes().contains(r) && e.getRoutes().contains(r)) {

								closbStop = s;
								closeStop = e;
								routeToTake = r;

								corrDirection = toDirection(s, endPoint);
								break;
							}
						}
					}
					busAtStart.remove(s);
				}
			}


			System.out.println("Does this final route exist?????????? :" + routeToTake.getRouteNumber());
					
			
			System.out.println("What is this?:" + routeToTake.getRouteMapLocation());
			

			System.out.println("the route number is:" + routeToTake.getRouteNumber());
			
			/**
			 * parses the routes. Gets the URL in order to plot the routes asked in task 7.
			 */
			translinkService.parseKMZ(routeToTake);

			/**
			 * A new trip object
			 */
			
			if (closbStop == null|| closeStop == null || routeToTake == null) {
				return null;
			}

			trip = new Trip(closbStop, closeStop, forDirection(closbStop, endPoint) , routeToTake, false);

			System.out.println("made a trip!");



			return trip;

		}

		/**
		 * Returns the string of the direction by taking a busStop and comparing its latitude and longitude with that of its 
		 * destination
		 * 
		 * @param b is the BusStop  
		 * @param endPoint is the LaltLon of the final destination
		 * @return String of the direction
		 */
		public String toDirection(BusStop b, LatLon endPoint) {


			double sLat = ((b.getLatLon().getLatitude()));
			double eLat = (endPoint.getLatitude());

			double sLong =(b.getLatLon().getLongitude());
			double eLong =(endPoint.getLongitude());


			System.out.println(" R uuuuuuuu printed????");

			if(Math.abs(eLat - sLat) > Math.abs(eLong - sLong)) {

				if(eLat < sLat) {
					return "EAST";
				} else {

					return "WEST"; }
			}
			if(Math.abs(eLat - sLat) < Math.abs(eLong - sLong)) {

				if(eLong > sLong) {	

					return "NORTH";
				} else {

					return "SOUTH";
				}
			}
			return "n/a";



		}

		public String forDirection(BusStop b, LatLon endPoint) {

			double sLat = ((b.getLatLon().getLatitude()));
			double eLat = (endPoint.getLatitude());

			double sLong =(b.getLatLon().getLongitude());
			double eLong =(endPoint.getLongitude());


			System.out.println(" R uuuuuuuu printed????");

			if(Math.abs(eLat - sLat) > Math.abs(eLong - sLong)) {

				if(eLat > sLat) {
					return "EAST";
				} else {

					return "WEST"; }
			}
			if(Math.abs(eLat - sLat) < Math.abs(eLong - sLong)) {

				if(eLong > sLong) {	

					return "NORTH";
				} else {

					return "SOUTH";
				}
			}
			return "n/a";
		}
		/**
		 * Method used in order to determine if BUStop at the Start and at the end are whithin walking distace of one another
		 * @param busAtStart
		 * @param busAtEnd
		 * @return boolean
		 */
		public boolean walkDistance(Set<BusStop> busAtStart, Set<BusStop> busAtEnd) {

			for (BusStop b1 : busAtStart) {
				for (BusStop b2 : busAtEnd) {

					if (b1.equals(b2)) {

						System.out.println("you can proceed to the selected Waldo");
						return true;
					}
					System.out.println("you must move in the speified direction");
				}
			}

			return false;
		}

		/**
		 * This method is used to pick the BusStop that is closest to the user.
		 * @param startOrEndPoint
		 * @param corrbStop
		 * @return BusStop
		 */
		public BusStop closeBusStop(LatLon startOrEndPoint, Set<BusStop> corrbStop) {
			System.out.println("boooooooooogieeee2222");
			double minDistance = 99999; 

			BusStop closestStop = null;
			for(BusStop b: corrbStop) {
				double currDistance = LatLon.distanceBetweenTwoLatLon(startOrEndPoint, b.getLatLon());
				System.out.println("boooooooooogieeee");
				if(currDistance < minDistance) {

					currDistance = minDistance;	

					closestStop = b;
				}

			}
			return closestStop;

		}
		/**
		 * Method used in order to determine if the BusStops at the start and at the end contain the same routes. This essage also is used
		 * to get bus info by calling translink.getBUsEstimtesForStop(BusStop ....). Different print statements are used in order to determine the progress
		 * of the Method.
		 * @param busAtStart
		 * @param busAtEnd
		 * @return 
		 */

		public Set<BusRoute> commBusRoutes(Set<BusStop> busAtStart, Set<BusStop> busAtEnd) {

			Set<BusRoute> busStartRoutes = new HashSet<BusRoute>();
			for(BusStop b1 : busAtStart) {
				translinkService.getBusEstimatesForStop(b1);
				
				for(BusRoute r : b1.getRoutes()) {

					if(translinkService.lookupRoute(r.getRouteNumber()) != null) {
						System.out.println("ADDDDEDDDDD BUSSSSSSSSSS");
						busStartRoutes.add(r);
					}
				}
			}

			Set<BusRoute> busEndRoutes = new HashSet<BusRoute>();

			for(BusStop b2: busAtEnd) {
				translinkService.getBusEstimatesForStop(b2);
				for(BusRoute r: b2.getRoutes()) {

					if(translinkService.lookupRoute(r.getRouteNumber()) != null) {
						System.out.println("ADDDDEDDDDD BUSSSSSSSSSS 22");
						busEndRoutes.add(r);
					}
				}
			}

			Set<BusRoute> commonBusRoutes = new HashSet<BusRoute>();
			for(BusRoute r1 : busStartRoutes) {
				if(busEndRoutes.contains(r1)) {

					BusRoute nr = translinkService.lookupRoute(r1.getRouteNumber());
					commonBusRoutes.add(nr);
					System.out.println("bus routes not added check 1"+ commonBusRoutes.isEmpty() );
					nr.getBuses();
					System.out.println("does r1 not return any buses?"+ r1.getBuses().isEmpty() );
				}
			}
			System.out.println("Bus routes not added check 2" + commonBusRoutes.isEmpty());
			translinkService.addToRoutes(commonBusRoutes);
			return commonBusRoutes;

		}



		@Override
		protected void onPostExecute(Trip trip) {
			dialog.dismiss();

			if (trip != null && !trip.inWalkingDistance()) {
				// Remove previous start/end stops
				busStopToBoardOverlay.removeAllItems();
				busStopToDisembarkOverlay.removeAllItems();

				// Removes all but the selected Waldo
				waldosOverlay.removeAllItems();
				List<Waldo> waldos = new ArrayList<Waldo>();
				waldos.add(selectedWaldo);
				plotWaldos(waldos);

				// Plot the route
				plotRoute(trip);

				// Move map to the starting location
				LatLon startPointLatLon = trip.getStart().getLatLon();
				mapController.setCenter(new GeoPoint(startPointLatLon
						.getLatitude(), startPointLatLon.getLongitude()));
				mapView.invalidate();
			} else if (trip != null && trip.inWalkingDistance()) {
				AlertDialog dialog = createSimpleDialog("You are in walking distance!");
				dialog.show();
			} else {
				AlertDialog dialog = createSimpleDialog("Unable to retrieve bus location info...");
				dialog.show();
			}
		}
	}

	/**
	 * Asynchronous task to initialize or re-initialize access to the Waldo web
	 * service.
	 */
	private class InitWaldo extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... arg0) {

			// Initialize the service passing the name of the Waldo to use. If
			// you have
			// passed an argument to this task, then it will be used as the
			// name, otherwise
			// nameToUse will be null
			String nameToUse = arg0[0];
			userName = waldoService.initSession(nameToUse);

			return null;
		}

	}

	/**
	 * Asynchronous task to get Waldo points from Waldo web service. Displays
	 * progress dialog while running in background.
	 */
	private class GetWaldoLocations extends
	AsyncTask<Integer, Void, List<Waldo>> {
		private ProgressDialog dialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			dialog.setMessage("Retrieving locations of waldos...");
			dialog.show();
		}

		@Override
		protected List<Waldo> doInBackground(Integer... i) {
			Integer numberOfWaldos = i[0];
			return waldoService.getRandomWaldos(numberOfWaldos);
		}

		@Override
		protected void onPostExecute(List<Waldo> waldos) {
			dialog.dismiss();
			if (waldos != null) {
				plotWaldos(waldos);
			}
		}
	}

	/**
	 * Asynchronous task to get messages from Waldo web service. Displays
	 * progress dialog while running in background.
	 */
	private class GetMessagesFromWaldo extends
	AsyncTask<Void, Void, List<String>> {

		private ProgressDialog dialog = new ProgressDialog(getActivity());

		@Override
		protected void onPreExecute() {
			dialog.setMessage("Retrieving messages...");
			dialog.show();
		}

		@Override
		protected List<String> doInBackground(Void... params) {
			return waldoService.getMessages();
		}

		@Override
		protected void onPostExecute(List<String> messages) {
			// CPSC 210 Students: Complete this method
			dialog.dismiss();
			StringBuffer allMessages = new StringBuffer();
			if ((messages.isEmpty()) || messages == null) {
				allMessages.append("Inbox is empty.");
				System.out.println("printed an empty box");
			}
			else if (messages.size() >= 1) {
				allMessages.append("Inbox(" + messages.size() + ")");
				for (int i = 0; i < messages.size(); i++) {
					allMessages.append("\n" + messages.get(i));
				}
				System.out.println("printed a bunch of messages");
			}


			AlertDialog dialog = createSimpleDialog(allMessages.toString());
			dialog.show();

		}
	}
}
