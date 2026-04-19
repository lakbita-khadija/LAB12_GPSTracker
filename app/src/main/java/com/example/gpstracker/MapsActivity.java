package com.example.gpstracker;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * MapsActivity — affiche toutes les positions stockées en base sous forme de marqueurs.
 *
 * Flux :
 *  1. La carte est prête → onMapReady()
 *  2. POST vers showPositions.php → réponse JSON { "positions": [...] }
 *  3. Pour chaque position → ajout d'un marqueur rose sur la carte
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    /** ⚠️ Même IP que dans MainActivity */
    private static final String URL_POSITIONS =
            "http://10.0.2.2/localisation/showPositions.php";

    private GoogleMap    carteGoogle;
    private RequestQueue fileRequetes;

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        fileRequetes = Volley.newRequestQueue(getApplicationContext());

        // Initialiser le fragment carte
        SupportMapFragment fragmentCarte =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        if (fragmentCarte != null) {
            fragmentCarte.getMapAsync(this);
        }
    }

    // ── Carte prête ──────────────────────────────────────────────────────────

    @Override
    public void onMapReady(GoogleMap googleMap) {
        carteGoogle = googleMap;

        // Style minimal de la carte
        carteGoogle.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Charger et afficher les marqueurs
        chargerEtAfficherPositions();
    }

    // ── Chargement des positions ─────────────────────────────────────────────

    /**
     * Appelle showPositions.php en POST et place un marqueur rose
     * pour chaque position reçue.
     */
    private void chargerEtAfficherPositions() {

        JsonObjectRequest requete = new JsonObjectRequest(
                Request.Method.POST,
                URL_POSITIONS,
                null,   // pas de corps JSON à envoyer

                // ── Succès ──────────────────────────────────────────────────
                reponse -> {
                    try {
                        JSONArray listePositions = reponse.getJSONArray("positions");
                        int total = listePositions.length();

                        if (total == 0) {
                            Toast.makeText(this,
                                    "Aucune position en base", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        LatLng dernierePosition = null;

                        for (int i = 0; i < total; i++) {
                            JSONObject item = listePositions.getJSONObject(i);

                            double lat = item.getDouble("latitude");
                            double lon = item.getDouble("longitude");
                            String dateStr = item.optString("date", "");

                            LatLng coordonnees = new LatLng(lat, lon);

                            // Marqueur rose pour la dernière position, rouge pour les autres
                            float couleur = (i == total - 1)
                                    ? BitmapDescriptorFactory.HUE_ROSE
                                    : BitmapDescriptorFactory.HUE_RED;

                            carteGoogle.addMarker(new MarkerOptions()
                                    .position(coordonnees)
                                    .title("Position #" + (i + 1))
                                    .snippet(dateStr)
                                    .icon(BitmapDescriptorFactory.defaultMarker(couleur)));

                            dernierePosition = coordonnees;
                        }

                        // Centrer la carte sur la position la plus récente
                        if (dernierePosition != null) {
                            carteGoogle.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(dernierePosition, 16f));
                        }

                        Toast.makeText(this,
                                total + " position(s) affichée(s)", Toast.LENGTH_SHORT).show();

                    } catch (JSONException e) {
                        Toast.makeText(this,
                                "Erreur JSON : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },

                // ── Erreur réseau ────────────────────────────────────────────
                erreur -> Toast.makeText(this,
                        "Erreur réseau : " + erreur.getMessage(),
                        Toast.LENGTH_LONG).show()
        );

        fileRequetes.add(requete);
    }
}