package com.vlille.checker.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.vlille.checker.Application;
import com.vlille.checker.R;
import com.vlille.checker.db.MetadataEntityManager;
import com.vlille.checker.db.StationEntityManager;
import com.vlille.checker.model.Metadata;
import com.vlille.checker.ui.osm.location.LocationManagerWrapper;
import com.vlille.checker.utils.ContextHelper;
import com.vlille.checker.utils.PreferenceKeys;

import org.droidparts.Injector;
import org.droidparts.annotation.inject.InjectDependency;

import java.text.SimpleDateFormat;
import java.util.Date;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class SettingsFragment extends PreferenceFragmentCompat
        implements OnSeekBarChangeListener {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private LocationManagerWrapper locationManagerWrapper;
    private boolean hasClickedOnLocalisationActivationAndNeedsGpsCheck;

    @InjectDependency
    protected StationEntityManager stationEntityManager;

    @InjectDependency
    protected MetadataEntityManager metadataEntityManager;

    @Override
    public final View onCreateView(LayoutInflater inflater,
                                   ViewGroup container, Bundle savedInstanceState) {
        View view = onCreateView(savedInstanceState, inflater, container);
        Injector.inject(view, this);

        initView();

        return view;
    }

    public View onCreateView(Bundle savedInstanceState,
                             LayoutInflater inflater, ViewGroup container) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        addPreferencesFromResource(R.xml.preferences);

        locationManagerWrapper = LocationManagerWrapper.with(getActivity());
    }

    private void initView() {
        setLastDataStatusUpdate();
        setVersionNumber();
        onChangeGpsActivated();
        onChangeRadiusValue();
    }

    private void setLastDataStatusUpdate() {
        final Preference lastUpdatePreference = findPreference(PreferenceKeys.DATA_STATUS_LAST_UPDATE);
        lastUpdatePreference.setTitle(getDataStatusStationsNumber());
        lastUpdatePreference.setSummary(getDataStatusLastUpdateMessage());
    }

    private void setVersionNumber() {
        Preference preference = findPreference(PreferenceKeys.ABOUT_VERSION);
        preference.setTitle(Application.getVersionNumber());
    }

    private String getDataStatusStationsNumber() {
        return getString(R.string.data_status_stations_number, stationEntityManager.count());
    }

    private String getDataStatusLastUpdateMessage() {
        String lastUpdateFormatted = getDataStatusLastUpdateMessageFormatted();

        return getString(R.string.data_status_last_update_summary, lastUpdateFormatted);
    }

    private String getDataStatusLastUpdateMessageFormatted() {
        Metadata metadata = metadataEntityManager.find();
        if (metadata == null) {
            return getString(R.string.data_status_never);
        }

        Date lastUpdate = new Date(metadata.getLastUpdate());
        String formatPattern = getString(R.string.data_status_date_pattern);

        return new SimpleDateFormat(formatPattern).format(lastUpdate);
    }

    private void onChangeGpsActivated() {
        final Preference prefGpsProviderOn = findPreference(PreferenceKeys.LOCALISATION_GPS_ACTIVATED);
        prefGpsProviderOn.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(PreferenceKeys.LOCALISATION_GPS_ACTIVATED)) {
                    boolean enabled = (Boolean) newValue;
                    if (enabled && !locationManagerWrapper.isGpsProviderEnabled()) {
                        Log.d(TAG, "Localisation enabled and provider is off");

                        hasClickedOnLocalisationActivationAndNeedsGpsCheck = true;
                        locationManagerWrapper.createGpsDisabledAlert();

                        return false;
                    }
                }

                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (hasClickedOnLocalisationActivationAndNeedsGpsCheck
                && locationManagerWrapper.isGpsProviderEnabled()) {
            hasClickedOnLocalisationActivationAndNeedsGpsCheck = false;

            Log.d(TAG, "Gps has been activated, set maps location prefs enabled on");
            SharedPreferences.Editor editor = getDefaultSharedPreferences(getContext()).edit();
            editor.putBoolean(PreferenceKeys.LOCALISATION_GPS_ACTIVATED, true);
            editor.apply();

            // Restart activity to refresh the preferences.
            startActivity(getActivity().getIntent());
        }
    }

    //=========================
    // Location radius onChange
    //=========================

    private Preference prefLocationRadiusValue;
    private SeekBar seekBar;
    private TextView textProgress;

    private void onChangeRadiusValue() {
        prefLocationRadiusValue = findPreference(PreferenceKeys.POSITION_RADIUS);
        prefLocationRadiusValue.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Log.d(TAG, "Show the dialog with slider radius");

                        View view = View.inflate(getActivity(),
                                R.layout.settings_position_slider, null);
                        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                                .setTitle(getString(R.string.prefs_position_distance))
                                .setView(view)
                                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        int progress = seekBar.getProgress();
                                        Log.d(TAG, "Save the radius " + progress);

                                        if (progress == 0) {
                                            progress = (int) PreferenceKeys.POSITION_RADIUS_DEFAULT_VALUE;
                                        }

                                        final SharedPreferences.Editor editor = getDefaultSharedPreferences(getContext()).edit();
                                        editor.putLong(PreferenceKeys.POSITION_RADIUS, Long.valueOf(progress));
                                        editor.apply();

                                        changePrefLocationRadiusValue();
                                    }
                                })
                                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                                .create();
                        alertDialog.show();

                        textProgress = (TextView) alertDialog.findViewById(R.id.position_distance_value);

                        seekBar = (SeekBar) alertDialog.findViewById(R.id.position_seekbar_distance);
                        seekBar.setOnSeekBarChangeListener(SettingsFragment.this);

                        updateSeekBarProgress();
                        changePrefLocationRadiusValue();

                        return false;
                    }
                });

        changePrefLocationRadiusValue();
    }

    /**
     * Build the summary displayed in the menu.
     */
    private void changePrefLocationRadiusValue() {
        String summary = String.format(
                "%s %d%s",
                getString(R.string.prefs_position_radius_distance_summary),
                ContextHelper.getRadiusValue(getActivity()),
                getString(R.string.prefs_position_radius_distance_unit));

        prefLocationRadiusValue.setSummary(summary);
    }

    private void updateSeekBarProgress() {
        seekBar.setProgress(Long.valueOf(ContextHelper.getRadiusValue(getActivity())).intValue());
    }

    /**
     * On radius change, update the text.
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        textProgress.setText(String.format("%d %s", progress, getString(R.string.prefs_position_radius_distance_unit)));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // should remain empty
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // should remain empty
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // should remain empty
    }

}
