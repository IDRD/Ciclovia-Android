package co.gov.idrd.ciclovia;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.constant.TransportMode;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Leg;
import com.akexorcist.googledirection.model.Route;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import co.gov.idrd.ciclovia.image.BitmapFromVectorFactory;
import co.gov.idrd.ciclovia.util.RequestCaller;
import co.gov.idrd.ciclovia.util.RequestManager;


/**
 * A simple {@link Fragment} subclass.
 */
public class Mapa extends Fragment implements View.OnClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowCloseListener, RequestCaller{

    private final int PERMISO_DE_RASTREO_UBICACION = 1;

    private Context context;
    private Activity activity;
    private MapView map;
    private GoogleMap gmap;
    private Button button_ir_al_punto;
    private LocationManager locationManager;
    private FloatingActionButton fab;

    private LocationTracker rastreador;
    private ArrayList<Corredor> corredores;
    private Polyline ruta_calculada;
    private Punto destino = null;
    private LatLng posicion_actual = null;

    private boolean en_seguimiento = false;
    private boolean en_ruta = false;

    public Mapa() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_mapa, container, false);

        context = getContext();
        activity = getActivity();
        corredores = new ArrayList<Corredor>();

        fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        map = (MapView) rootView.findViewById(R.id.map);
        map.onCreate(savedInstanceState);
        map.onResume(); // needed to get the map to display immediately
        button_ir_al_punto = (Button) rootView.findViewById(R.id.ir_al_punto);

        fab.setOnClickListener(this);
        button_ir_al_punto.setOnClickListener(this);

        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        map.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap mMap) {
                gmap = mMap;
                gmap.clear();
                gmap.setOnMarkerClickListener(Mapa.this);
                gmap.setOnInfoWindowCloseListener(Mapa.this);
                gmap.getUiSettings().setMapToolbarEnabled(false);

                // For dropping a marker at a point on the Mapa
                Mapa.this.rastreador = new LocationTracker(activity, gmap);
                Mapa.this.cargarCorredores();
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //you can set the title for your toolbar here for different fragments different titles
        getActivity().setTitle("Mapa del sistema");
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        map.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        map.onLowMemory();
    }

    @Override
    public void onClick(View view) {
        // rastreador.iniciarRastreo();
        switch (view.getId())
        {
            case R.id.ir_al_punto:
                rastreador.iniciarRastreo();
                en_ruta = true;
            break;
        }
    }

    private void cargarCorredores() {
        String api = "api/corredores/obtener";

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            Mapa.URL + api,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        JSONArray json_corredores = response.getJSONArray("corredores");
                        ruta_calculada = gmap.addPolyline(new PolylineOptions().width(12f).color(Color.rgb(96, 125, 139)));

                        // dibujar la ruta
                        for (int i = 0; i < json_corredores.length(); i++) {
                            Corredor corredor = Corredor.crearCorredorDeJSONObject(json_corredores.getJSONObject(i));
                            corredores.add(corredor);
                            gmap.addPolyline(corredor.obtenerRuta().width(12f).color(Color.rgb(176, 190, 197)));

                            ArrayList<Punto> puntos = corredor.obtenerPuntos();
                            for (int j = 0; j < puntos.size(); j++) {
                                Punto punto = puntos.get(j);
                                int id_icon = BitmapFromVectorFactory.getResourcesIdFromString(Mapa.this.getContext(), punto.getIcono());
                                Marker temp = gmap.addMarker(new MarkerOptions()
                                        .position(punto.getLatLng())
                                        .title(punto.getNombre())
                                        .snippet(punto.getDescripcion())
                                        .icon(BitmapFromVectorFactory.fromResource(Mapa.this.getContext(), id_icon > 0 ? id_icon : R.drawable.ic_marcador_default))
                                );
                                temp.setTag(punto);
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(Mapa.TAG, error.toString());
                }
            }
        );

        RequestManager.getInstance(Mapa.this.getContext()).addToRequestQueue(request);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        destino = (Punto) marker.getTag();
        button_ir_al_punto.setVisibility(View.VISIBLE);

        return false;
    }

    @Override
    public void onInfoWindowClose(Marker marker) {
        button_ir_al_punto.setVisibility(View.INVISIBLE);
    }

    private class LocationTracker implements LocationListener, DirectionCallback, ActivityCompat.OnRequestPermissionsResultCallback {
        private LocationManager locationManager;
        private String[] permisos = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        private GoogleMap map;
        private Location bogota;

        public LocationTracker(Activity activity, GoogleMap map) {
            this.locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
            this.map = map;
            bogota = new Location("Bogota");
            bogota.setLatitude(4.6097100);
            bogota.setLongitude(-74.0817500);
            moverCamara(bogota, false);
        }

        @Override
        public void onLocationChanged(Location location) {
            LatLng actual = new LatLng(location.getLatitude(), location.getLongitude());
            if (en_ruta)
            {
                GoogleDirection.withServerKey("AIzaSyAtoqLzwwEf2ZWa6MvmgqloZMe9YILPurE")
                                .from(actual)
                                .to(destino.getLatLng())
                                .transportMode(TransportMode.WALKING)
                                .execute(this);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            switch (requestCode) {
                case PERMISO_DE_RASTREO_UBICACION:
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(activity, "Permission Granted!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Permission Denied!", Toast.LENGTH_SHORT).show();
                    }
            }
        }

        @Override
        public void onDirectionSuccess(Direction direction, String rawBody) {
            Route route = direction.getRouteList().get(0);
            Leg leg = route.getLegList().get(0);
            ArrayList<LatLng> coordenadas = leg.getDirectionPoint();
            if (ruta_calculada != null) {
                ruta_calculada.remove();

                ruta_calculada = gmap.addPolyline(new PolylineOptions().addAll(coordenadas).width(12f).color(Color.rgb(96, 125, 139)));
            }
        }

        @Override
        public void onDirectionFailure(Throwable t) {
            Log.v(Mapa.TAG, t.getMessage());
            try {
                throw t;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        public void iniciarRastreo() {
            if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(activity, permisos, PERMISO_DE_RASTREO_UBICACION);

                return;
            }

            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            else
                this.mostrarAlertaActivarGPS();

            gmap.setMyLocationEnabled(true);
        }

        private void mostrarAlertaActivarGPS() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage("Tu GPS parece estar deshabilitado, deseas habilitarlo?")
                    .setCancelable(false)
                    .setPositiveButton("Si", new DialogInterface.OnClickListener() {
                        public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        }

        private void moverCamara(Location location, boolean tracking) {
            LatLng coordenadas = new LatLng(location.getLatitude(), location.getLongitude());
            CameraPosition cameraPosition = null;
            cameraPosition = new CameraPosition.Builder().target(coordenadas).zoom(11).build();

            gmap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }
}
