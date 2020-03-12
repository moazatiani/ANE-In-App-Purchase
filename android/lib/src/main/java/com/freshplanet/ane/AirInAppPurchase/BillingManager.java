package com.freshplanet.ane.AirInAppPurchase;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class BillingManager {

    private boolean _debugLog = false;
    private boolean _disposed = false;
    private String _debugTag = "BillingManager";
    private boolean _setupDone = false;
    private Context _context;
    private BillingClient _billingClient;



    public interface SetupFinishedListener {

        void SetupFinished(Boolean success);
    }

    public interface QueryInventoryFinishedListener {

        void onQueryInventoryFinished(Boolean success, String data);
    }

    public interface QueryPurchasesFinishedListener {

        void onQueryPurchasesFinished(Boolean success, String data);
    }

    public interface PurchaseFinishedListener{

        void onPurchasesFinished(Boolean success, String data);
    }

    private interface GetProductInfoFinishedListener {

        void onGetProductInfoFinishedListener(List<SkuDetails> skuDetailsList);
    }

    BillingManager(Context ctx) {

        _context = ctx;

    }

    void dispose() {

        if(_billingClient != null)
            _billingClient.endConnection();
        _disposed = true;
    }



    void initialize(final SetupFinishedListener setupFinishedListener, final PurchasesUpdatedListener purchasesUpdatedListener) {

        checkNotDisposed();
        if (_setupDone) throw new IllegalStateException("BillingManager is already set up.");
        try {
            _billingClient = BillingClient.newBuilder(_context)
                    .setListener(purchasesUpdatedListener)
                    .enablePendingPurchases()
                    .build();
            _billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult billingResult) {

                    if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                        // The BillingClient is ready. You can query purchases here.
                        logDebug("BillingManager connected");
                        setupFinishedListener.SetupFinished(true);


                    }
                }
                @Override
                public void onBillingServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                    logDebug("BillingManager disconnected");
                    if (_disposed) return;

                    setupFinishedListener.SetupFinished(false);
                    _billingClient = null;
                    _setupDone = true;

                }
            });
        }
        catch (Exception e) {
            logDebug("Error initializing BillingManager " + e.getLocalizedMessage());
        }

    }

    void queryInventory(List<String> skuList, final List<String> skuSubsList, final QueryInventoryFinishedListener listener) {

        try {
            checkNotDisposed();
            final List<SkuDetails> result = new ArrayList<SkuDetails>();


            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);

            getProductInfo(params.build(), new GetProductInfoFinishedListener() {
                @Override
                public void onGetProductInfoFinishedListener(List<SkuDetails> skuDetailsList) {

                    if(skuDetailsList != null) {
                        result.addAll(skuDetailsList);
                    }

                    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                    params.setSkusList(skuSubsList).setType(BillingClient.SkuType.SUBS);

                    getProductInfo(params.build(), new GetProductInfoFinishedListener() {
                        @Override
                        public void onGetProductInfoFinishedListener(List<SkuDetails> skuDetailsList) {

                            if(skuDetailsList != null) {
                                result.addAll(skuDetailsList);
                            }


                            JSONObject detailsObject = new JSONObject();

                            for (SkuDetails skuDetails : result) {
                                try {
                                    detailsObject.put(skuDetails.getSku(), new JSONObject(skuDetails.getOriginalJson()));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            JSONObject resultObject = new JSONObject();
                            try {
                                resultObject.put("details", detailsObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            listener.onQueryInventoryFinished(true, resultObject.toString());

                        }
                    });


                }
            });

        }
        catch (Exception e) {
            listener.onQueryInventoryFinished(false, e.getLocalizedMessage());
        }

    }

    private void getProductInfo(SkuDetailsParams params, final GetProductInfoFinishedListener listener) {

        try {
            checkNotDisposed();
            _billingClient.querySkuDetailsAsync(params,
                    new SkuDetailsResponseListener() {
                        @Override
                        public void onSkuDetailsResponse(BillingResult billingResult,
                                                         List<SkuDetails> skuDetailsList) {
                            // Process the result.
                            listener.onGetProductInfoFinishedListener(skuDetailsList);
                        }
                    });
        }
        catch (Exception e) {
            listener.onGetProductInfoFinishedListener(null);
        }
    }

    void queryPurchases(final QueryPurchasesFinishedListener listener) {

        try {
            checkNotDisposed();

            List<Purchase> purchases = new ArrayList<Purchase>();

            Purchase.PurchasesResult purchasesResult = _billingClient.queryPurchases(BillingClient.SkuType.INAPP);

            if(purchasesResult.getBillingResult().getResponseCode() == BillingClient.BillingResponseCode.OK) {
                purchases.addAll(purchasesResult.getPurchasesList());
            }
            else {
                // report errors
                listener.onQueryPurchasesFinished(false, purchasesResult.getBillingResult().getDebugMessage());
                return;

            }

            Purchase.PurchasesResult subscriptionsResult = _billingClient.queryPurchases(BillingClient.SkuType.SUBS);
            if(subscriptionsResult.getBillingResult().getResponseCode() == BillingClient.BillingResponseCode.OK) {
                purchases.addAll(subscriptionsResult.getPurchasesList());
            }
            else {
                // report errors
                listener.onQueryPurchasesFinished(false, purchasesResult.getBillingResult().getDebugMessage());
                return;
            }


            final JSONObject resultObject = new JSONObject();
            final JSONObject purchasesObject = new JSONObject();

            for (Purchase p : purchases) {

                try {
                    purchasesObject.put(p.getSku(), new JSONObject(p.getOriginalJson()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            resultObject.put("purchases", purchasesObject);
            listener.onQueryPurchasesFinished(true, resultObject.toString());

        }
        catch (Exception e) {
            listener.onQueryPurchasesFinished(false,e.getLocalizedMessage());
        }


    }

    void purchaseProduct(final Activity activity, String skuID, String productType, final PurchaseFinishedListener listener) {

        try {
            if(!productType.equals(BillingClient.SkuType.INAPP) && !productType.equals(BillingClient.SkuType.SUBS)) {
                listener.onPurchasesFinished(false, "Unknown product type");
                return;
            }

            if(productType.equals(BillingClient.SkuType.SUBS) && _billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).getResponseCode() != BillingClient.BillingResponseCode.OK) {
                listener.onPurchasesFinished(false, "Subscriptions are not available.");
                return;
            }

            final SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(Arrays.asList(skuID)).setType(productType);
            getProductInfo(params.build(), new GetProductInfoFinishedListener() {
                @Override
                public void onGetProductInfoFinishedListener(List<SkuDetails> skuDetailsList) {
                    if(skuDetailsList != null && skuDetailsList.size() > 0) {

                        SkuDetails details = skuDetailsList.get(0);
                        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                                .setSkuDetails(details)
                                .build();
                        _billingClient.launchBillingFlow(activity, flowParams);

                    }
                    else {
                        listener.onPurchasesFinished(false, "Unknown product");
                    }
                }
            });
        }
        catch (Exception e) {
            listener.onPurchasesFinished(false,e.getLocalizedMessage());
        }


    }


    void consumePurchase(String purchaseToken, String developerPayload, ConsumeResponseListener listener) {
        try {
            checkNotDisposed();
            ConsumeParams.Builder paramsBuilder = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchaseToken);

            if(developerPayload != null && !developerPayload.equals("")) {
                paramsBuilder.setDeveloperPayload(developerPayload);
            }

            _billingClient.consumeAsync(paramsBuilder.build(),listener);
        }
        catch (Exception e) {
            BillingResult result = BillingResult.newBuilder().setDebugMessage(e.getLocalizedMessage()).setResponseCode(BillingClient.BillingResponseCode.ERROR).build();
            listener.onConsumeResponse(result,result.getDebugMessage());
        }
    }


    /**
     * Enables or disable debug logging through LogCat.
     */
    void enableDebugLogging(boolean enable, String tag) {
        checkNotDisposed();
        _debugLog = enable;
        _debugTag = tag;
    }

    public void enableDebugLogging(boolean enable) {
        checkNotDisposed();
        _debugLog = enable;
    }

    private void checkNotDisposed() {
        if (_disposed) throw new IllegalStateException("BillingManager was disposed of, so it cannot be used.");
    }


    void logDebug(String msg) {
        if (_debugLog) Log.d(_debugTag, msg);
    }
}