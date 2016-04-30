package tech.jonas.mondoandroid.api.authentication;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;

import com.f2prateek.rx.preferences.Preference;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import okhttp3.HttpUrl;
import rx.Observable;
import tech.jonas.mondoandroid.R;
import tech.jonas.mondoandroid.api.Config;
import tech.jonas.mondoandroid.api.GcmService;
import tech.jonas.mondoandroid.api.MondoService;
import tech.jonas.mondoandroid.api.OauthConfig;
import tech.jonas.mondoandroid.api.model.RegistrationToken;
import tech.jonas.mondoandroid.api.model.Webhook;
import tech.jonas.mondoandroid.data.IntentFactory;
import tech.jonas.mondoandroid.di.scopes.ApiScope;
import tech.jonas.mondoandroid.utils.ListUtils;

@ApiScope
public class OauthManager {
    private final IntentFactory intentFactory;
    private final Preference<String> accessToken;
    private final Preference<String> refreshToken;
    private final Preference<String> webhook;
    private final Application application;
    private final MondoService mondoService;
    private final GcmService gcmService;

    private final AtomicBoolean refreshingToken = new AtomicBoolean(false);

    @Inject
    public OauthManager(IntentFactory intentFactory, MondoService mondoService, GcmService gcmService,
                        @WebhookId Preference<String> webhook,
                        @AccessToken Preference<String> accessToken, @RefreshToken Preference<String> refreshToken,
                        Application application) {
        this.intentFactory = intentFactory;
        this.mondoService = mondoService;
        this.gcmService = gcmService;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.webhook = webhook;
        this.application = application;
    }

    public static String getAuthUrl() {
        final StringBuilder urlBuilder = new StringBuilder(Config.LOGIN_URL);
        urlBuilder.append("?client_id=").append(OauthConfig.CLIENT_ID);
        urlBuilder.append("&redirect_uri=").append(Config.REDIRECT_URL);
        urlBuilder.append("&response_type=code");

        return urlBuilder.toString();
    }

    public Observable<String> getAuthToken(Uri data) {
        final Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", OauthConfig.CLIENT_ID);
        params.put("client_secret", OauthConfig.CLIENT_SECRET);
        params.put("redirect_uri", Config.REDIRECT_URL);
        params.put("code", data.getQueryParameter("code"));
        return mondoService.getAccessToken(params)
                .doOnNext(tokenResponse -> accessToken.set(tokenResponse.accessToken))
                .doOnNext(tokenResponse -> refreshToken.set(tokenResponse.refreshToken))
                .map(accessTokenResponse -> accessTokenResponse.accessToken);
    }

    public Observable<String> refreshAuthToken() {
        if (refreshingToken.getAndSet(true)) {
            return Observable.empty();
        }
        accessToken.delete();
        final Map<String, String> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("client_id", OauthConfig.CLIENT_ID);
        params.put("client_secret", OauthConfig.CLIENT_SECRET);
        params.put("refresh_token", refreshToken.get());
        return mondoService.getAccessToken(params)
                .doOnNext(tokenResponse -> accessToken.set(tokenResponse.accessToken))
                .doOnNext(tokenResponse -> refreshToken.set(tokenResponse.refreshToken))
                .map(accessTokenResponse -> accessTokenResponse.accessToken)
                .doOnNext(accessToken -> refreshingToken.set(false));
    }

    public Observable<Webhook> registerWebhook() {
        return mondoService.getAccounts().flatMap(accounts ->
                Observable.<RegistrationToken>create(subscriber -> {
                    InstanceID instanceID = InstanceID.getInstance(application);
                    try {
                        final String token = instanceID.getToken(application.getString(R.string.gcm_defaultSenderId),
                                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(new RegistrationToken(ListUtils.first(accounts.accounts).id, token));
                            subscriber.onCompleted();
                        }
                    } catch (IOException e) {
                        subscriber.onError(e);
                    }
                })).flatMap(registrationToken -> gcmService.uploadToken(registrationToken)
                .flatMap(registrationAnswer -> mondoService.deleteWebhook(webhook.get()).onErrorResumeNext(Observable.just(null)))
                .flatMap(ignore -> mondoService.registerWebhook(registrationToken.accountId, Config.WEBHOOK_URL)))
                .map(webhookResponse -> webhookResponse.webhook)
                .doOnNext(webhook -> this.webhook.set(webhook.id));
    }

    public boolean isAuthenticated() {
        return accessToken.isSet();
    }

    public void logout() {
        accessToken.delete();
        refreshToken.delete();
    }
}