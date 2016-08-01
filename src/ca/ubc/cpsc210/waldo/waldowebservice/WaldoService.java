package ca.ubc.cpsc210.waldo.waldowebservice;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;
import ca.ubc.cpsc210.waldo.exceptions.WaldoException;
import ca.ubc.cpsc210.waldo.model.Waldo;
import ca.ubc.cpsc210.waldo.util.LatLon;

public class WaldoService {

	int tstamp;

	LatLon loc;

	String name;
	String key; 

	List<Waldo> WaldoList;

	private List<String> messages = new ArrayList<String>();
	
	private final static String WALDO_WEB_SERVICE_URL = "http://kramer.nss.cs.ubc.ca:8080/";

	/**
	 * Constructor
	 */
	public WaldoService() {

		WaldoList = new ArrayList<Waldo>();
	}

	/**
	 * Execute a given query
	 * 
	 * @param urlBuilder
	 *            The query with everything but http:
	 * @return The JSON returned from the query
	 */
	private String makeJSONQuery(StringBuilder urlBuilder) {
		try {
			URL url = new URL(urlBuilder.toString());

			// URL url = new URL(WALDO_WEB_SERVICE_URL);
			HttpURLConnection client = (HttpURLConnection) url.openConnection();

			// client.setRequestProperty("accept", "application/json");

			InputStream in = client.getInputStream();

			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String returnString = br.readLine();

			client.disconnect();

			return returnString;

		} catch (Exception e) {
			throw new WaldoException("Unable to make JSON query: "
					+ urlBuilder.toString());
		}
	}

	/**
	 * Initialize a session with the Waldo web service. The session can time out
	 * even while the app is active...
	 * 
	 * @param nameToUse
	 *            The name to go register, can be null if you want Waldo to
	 *            generate a name
	 * @return The name that Waldo gave you
	 */
	public String initSession(String nameToUse) {
		// CPSC 210 Students. You will need to complete this method
		// Format the request string

		StringBuilder urlBuilder = new StringBuilder(WALDO_WEB_SERVICE_URL
				+ "initsession/");

		// urlBuilder.append("initsession/");

		if (nameToUse == null) {

			nameToUse = "";
		}

		urlBuilder.append(nameToUse);

		System.out.println("booooooooooooooooosssssss");
		String s = makeJSONQuery(urlBuilder);
		System.out.println("baaaaaaaaaaaas");

		JSONTokener bog;

		try {

			System.out.println(" blaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
			bog = (JSONTokener) new JSONTokener(s);

			JSONObject bog2 = new JSONObject(bog);

			String key = bog2.getString("Key");
			this.key = key;
			Log.i("key:", key);
			System.out.println(key);

			String name = bog2.getString("Name");
			this.name = name;
			Log.i("name:", name);
			System.out.println(name);

			if (bog2.has("errorString")) {

				String errorString = bog2.getString("ErrorString");
				int errorNo = bog2.getInt("ErrorNumber");

				throw new WaldoException("ErrorNumber:" + errorNo
						+ "ErrorString:" + errorString);
			}

			return name;

		} catch (Exception e) {
			e.printStackTrace();

		}

		return null;
	}

	/**
	 * Get waldos from the Waldo web service.
	 * 
	 * @param numberToGenerate
	 *            The number of Waldos to try to retrieve
	 * @return Waldo objects based on information returned from the Waldo web
	 *         service
	 */
	public List<Waldo> getRandomWaldos(int numberToGenerate) {
		// CPSC 210 Students: You will need to complete this method

		StringBuilder urlBuilder = new StringBuilder(WALDO_WEB_SERVICE_URL);

		urlBuilder.append("getwaldos/" + key + "/" + numberToGenerate);

		String s = makeJSONQuery(urlBuilder);

		JSONTokener bog = new JSONTokener(s);

		try {

			JSONArray bog2 = new JSONArray(bog);

			if (numberToGenerate > 0) {

				for (int i = 0; i < bog2.length(); i++) {

					JSONObject bog3 = bog2.getJSONObject(i);

					String name = bog3.getString("Name");
					this.name = name;
					Log.i("name:", name);

					System.out.println(name);

					JSONObject loc = bog3.getJSONObject("Loc");
					// Log.("Loc:", loc);

					double lat = loc.getDouble("Lat");
					double lon = loc.getDouble("Long");

					LatLon l = new LatLon(lat, lon);

					int tstamp = loc.getInt("Tstamp");
					this.tstamp = tstamp;

					Date date = new Date(tstamp);

					Waldo w1 = new Waldo(name, date, l);

					WaldoList.add(w1);
					
				
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return WaldoList;
	}

	/**
	 * Return the current list of Waldos that have been retrieved
	 * 
	 * @return The current Waldos
	 */
	public List<Waldo> getWaldos() {
		// CPSC 210 Students: You will need to complete this method

		// for(Waldo w: WaldoList) {

		// if(k.equals("Name")) {

		// }

		if (!(WaldoList.isEmpty())) {

			return WaldoList;

		}
		return null;
	}

	/**
	 * Retrieve messages available for the user from the Waldo web service
	 * 
	 * @return A list of messages
	 */
	public List<String> getMessages() {
		// CPSC 210 Students: You will need to complete this method
		 try {
             messages.clear();
             StringBuilder urlBuilder = new StringBuilder(WALDO_WEB_SERVICE_URL + "getmsgs/");
             urlBuilder.append(key + "/");
             String s = makeJSONQuery(urlBuilder);          
             System.out.println(s);


             JSONTokener jt = new JSONTokener(s);
             //              try {

             JSONObject jo = new JSONObject(jt);
             if (!(jo.has(null))) {


                     ////                    if (!(messages.isEmpty())) {
                     //
                     //                              for (int i = 0; i < jo.length(); i++) {
                     //
                     //                                      JSONObject jo1 = ja.getJSONObject(i);
                     System.out.println("Where am i going wrong");

                     JSONArray jMessages = jo.getJSONArray("Messages");
                     System.out.println("got past previous trouble point?");


                     for (int j = 0; j < jMessages.length(); j++) {
                             JSONObject om = jMessages.getJSONObject(j);

                             String name = om.getString("Name").toString();
                             String message = om.getString("Message").toString();



                             String fullMessage ="Name: " + name + "\n" + message + "\n" + " ";

                             messages.add(fullMessage);

                     }

             }
             else
                     return messages;

             System.out.println(messages);


             //                              }

             //                      }

     } catch (JSONException je) {
             return messages;
     }
       catch (Exception e) {
             e.printStackTrace();
     }

     return messages;
		
		
	}

}
