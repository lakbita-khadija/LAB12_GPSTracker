package com.example.gpstracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int CODE_PERMISSION_LOCALISATION = 42;

    // ⚠️ Remplacer par l'IP de votre PC serveur
    private static final String URL_INSERTION =
            "http://10.0.2.2/localisation/createPosition.php";

    private static final long  INTERVALLE_MS = 60000L;
    private static final float DISTANCE_M    = 150f;

    private TextView tvLatitude;
    private TextView tvLongitude;
    private TextView tvStatut;

    private LocationManager gestLocalisations;
    private RequestQueue    fileRequetes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLatitude  = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
        tvStatut    = findViewById(R.id.tvStatut);
        Button btnCarte = findViewById(R.id.btnAfficherCarte);

        fileRequetes      = Volley.newRequestQueue(getApplicationContext());
        gestLocalisations = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        btnCarte.setOnClickListener(v ->
                startActivity(new Intent(this, MapsActivity.class))
        );

        verifierPermissionEtDemarrer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        gestLocalisations.removeUpdates(ecouteurGps);
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private void verifierPermissionEtDemarrer() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    CODE_PERMISSION_LOCALISATION);
        } else {
            demarrerSuiviGPS();
        }
    }

    @Override
    public void onRequestPermissionsResult(int codeRequete,
                                           @NonNull String[] permissions,
                                           @NonNull int[] resultats) {
        super.onRequestPermissionsResult(codeRequete, permissions, resultats);
        if (codeRequete == CODE_PERMISSION_LOCALISATION
                && resultats.length > 0
                && resultats[0] == PackageManager.PERMISSION_GRANTED) {
            demarrerSuiviGPS();
        } else {
            Toast.makeText(this,
                    "Permission localisation refusée", Toast.LENGTH_LONG).show();
        }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private void demarrerSuiviGPS() {
        tvStatut.setText("Statut : GPS en cours d'acquisition…");
        gestLocalisations.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                INTERVALLE_MS,
                DISTANCE_M,
                ecouteurGps
        );
    }

    private final LocationListener ecouteurGps = new LocationListener() {

        @Override
        public void onLocationChanged(@NonNull Location lieu) {
            double lat    = lieu.getLatitude();
            double lon    = lieu.getLongitude();
            double alt    = lieu.getAltitude();
            float  precis = lieu.getAccuracy();

            tvLatitude.setText("Latitude : " + lat);
            tvLongitude.setText("Longitude : " + lon);
            tvStatut.setText("Statut : position reçue ✓");

            String msg = String.format(Locale.getDefault(),
                    "Lat=%s | Lon=%s | Alt=%.1f m | Précision=%.0f m",
                    lat, lon, alt, (double) precis);
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

            envoyerPosition(lat, lon);
        }

        @Override
        public void onStatusChanged(String fournisseur, int statut, Bundle extras) {
            String libelle;
            switch (statut) {
                case LocationProvider.OUT_OF_SERVICE:          libelle = "HORS SERVICE";  break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE: libelle = "INDISPONIBLE";  break;
                case LocationProvider.AVAILABLE:               libelle = "DISPONIBLE";    break;
                default:                                       libelle = "INCONNU";
            }
            Toast.makeText(getApplicationContext(),
                    fournisseur + " → " + libelle, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderEnabled(@NonNull String fournisseur) {
            Toast.makeText(getApplicationContext(),
                    "Provider activé : " + fournisseur, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderDisabled(@NonNull String fournisseur) {
            Toast.makeText(getApplicationContext(),
                    "Provider désactivé : " + fournisseur, Toast.LENGTH_SHORT).show();
        }
    };

    // ── Volley ────────────────────────────────────────────────────────────────

    private void envoyerPosition(final double lat, final double lon) {

        StringRequest requete = new StringRequest(
                Request.Method.POST,
                URL_INSERTION,
                reponse -> tvStatut.setText("Statut : envoyé au serveur ✓"),
                erreur  -> Toast.makeText(getApplicationContext(),
                        "Erreur réseau : " + erreur.getMessage(),
                        Toast.LENGTH_SHORT).show()
        ) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                SimpleDateFormat sdf =
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                Map<String, String> parametres = new HashMap<>();
                parametres.put("latitude",  String.valueOf(lat));
                parametres.put("longitude", String.valueOf(lon));
                parametres.put("date",      sdf.format(new Date()));
                parametres.put("imei",      obtenirIdentifiantAppareil());
                return parametres;
            }
        };

        fileRequetes.add(requete);
    }

    // ── Identifiant appareil ─────────────────────────────────────────────────

    private String obtenirIdentifiantAppareil() {
        // ANDROID_ID : stable, aucune permission requise
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (androidId != null && !androidId.trim().isEmpty()) {
            return androidId;
        }
        return "APPAREIL_INCONNU";
    }
}