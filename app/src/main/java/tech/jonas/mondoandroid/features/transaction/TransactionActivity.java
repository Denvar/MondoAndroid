package tech.jonas.mondoandroid.features.transaction;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import javax.inject.Inject;

import tech.jonas.mondoandroid.R;
import tech.jonas.mondoandroid.api.ApiComponent;
import tech.jonas.mondoandroid.di.ComponentProvider;
import tech.jonas.mondoandroid.ui.custom.AmountView;
import tech.jonas.mondoandroid.ui.model.UiTransaction;
import tech.jonas.mondoandroid.utils.UiUtils;

public class TransactionActivity extends AppCompatActivity implements TransactionView {

    public static final String ARG_TRANSACTION = "arg_transaction";
    @Inject
    TransactionPresenter presenter;
    private AmountView amountView;
    private TextView averageSpendView;
    private TextView merchantView;
    private ImageView logoView;
    private MapView mapView;

    public static void start(Activity activity, UiTransaction transaction, View... views) {
        final Bundle extras = new Bundle();
        extras.putSerializable(ARG_TRANSACTION, transaction);
        final Intent activityIntent = new Intent(activity, TransactionActivity.class);
        activityIntent.putExtras(extras);
        ActivityOptionsCompat options = ActivityOptionsCompat.
                makeSceneTransitionAnimation(activity, UiUtils.toPairs(views));
        activity.startActivity(activityIntent, options.toBundle());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction);

        amountView = (AmountView) findViewById(R.id.amount);
        averageSpendView = (TextView) findViewById(R.id.tv_average);
        merchantView = (TextView) findViewById(R.id.tv_merchant);
        logoView = (ImageView) findViewById(R.id.iv_logo);
        mapView = (MapView) findViewById(R.id.map_view);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        mapView.onCreate(savedInstanceState);

        ApiComponent apiComponent = ((ComponentProvider<ApiComponent>) getApplicationContext()).getComponent();
        DaggerTransactionComponent.builder()
                .apiComponent(apiComponent)
                .transactionModule(new TransactionModule(this))
                .build().inject(this);

        final UiTransaction transaction = (UiTransaction) getIntent().getSerializableExtra(ARG_TRANSACTION);
        presenter.onBindView(transaction);

        // Postpone enter transition to avoid flashing navigation bar]
        postponeEnterTransition();
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                decor.getViewTreeObserver().removeOnPreDrawListener(this);
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        presenter.onUnBindView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void setMerchantName(String title) {
        merchantView.setText(title);
    }

    @Override
    public void addMapMarker(double lat, double lng, String title) {
        mapView.getMapAsync(googleMap -> {
            final LatLng latLng = new LatLng(lat, lng);
            googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(title));
        });
    }

    @Override
    public void moveMapTo(double lat, double lng) {
        mapView.getMapAsync(googleMap -> {
            final LatLng latLng = new LatLng(lat, lng);
            final CameraUpdate update = CameraUpdateFactory.newLatLng(latLng);
            googleMap.moveCamera(update);
        });
    }

    @Override
    public void setAmount(long integerPart, long fractionalPart) {
        amountView.setAmount(integerPart, fractionalPart);
    }

    @Override
    public void setAverageSpend(String averageSpend) {
        averageSpendView.setText(averageSpend);
    }


    @Override
    public void setLogoUrl(String logoUrl) {
        Glide.with(this)
                .load(logoUrl)
                .centerCrop()
                .into(logoView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                supportFinishAfterTransition();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
