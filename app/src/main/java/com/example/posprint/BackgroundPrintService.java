package com.example.posprint;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class BackgroundPrintService extends IntentService {
    public BackgroundPrintService() {
        super("BackgroundPrintService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        Log.d("PrintService", "Started with URI: " + data);

        if (data == null) return;

        try {
            String raw = data.toString();

            String query = raw.substring(raw.indexOf('?') + 1);
            Map<String,String> params = Uri.parse(raw).getQueryParameterNames().stream()
                    .collect(Collectors.toMap(name -> name, name -> Uri.parse(raw).getQueryParameter(name)));

                // ✅ Manually extract base_url so & inside it isn't lost
                String baseUrl = null;
                int idx = raw.indexOf("base_url=");
                if (idx != -1) {
                    baseUrl = raw.substring(idx + "base_url=".length());
                    baseUrl = URLDecoder.decode(baseUrl, "UTF-8");
                }

                if (baseUrl == null) {
                    Log.e("PrintService", "No base_url found in deep link");
                    return;
                }

                // Ensure HTTPS
                if (baseUrl.startsWith("http://")) {
                    baseUrl = baseUrl.replace("http://", "https://");
                }

                // ✅ Add split_id if invoice_print and missing
                if (baseUrl.contains("invoice_print") && !baseUrl.contains("split_id=")) {
                    if (baseUrl.contains("?")) {
                        baseUrl += "&split_id=1";
                    } else {
                        baseUrl += "?split_id=1";
                    }
                }
                if(baseUrl.contains("print_today_petty_cash")){
                    int index = baseUrl.indexOf("&");
                    if (index != -1) {
                        baseUrl = baseUrl.substring(0, index);
                    }
                }

                Log.d("PrintService", "Final API URL: " + baseUrl);

            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, baseUrl, null,
                    response -> {
                        try {

                            String type = response.getString("print_type");
                            Log.d("PrintService", "Response OK, type=" + type);
                            if ("kot".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                new KOTHandler_FONT_API(getApplicationContext(), response, details).handleKOT();

                            }else if ("reprint_kot".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                new KOTHandler_FONT_API(getApplicationContext(), response, details).handleKOT();


                            }else if ("online_kot".equalsIgnoreCase(type)) {
                                /*JSONObject details = response.getJSONObject("details");
                                new KOTHandlerOnline(getApplicationContext(), response, details).handleKOT();*/

                                JSONObject details = response.getJSONObject("details");
                                new KOTHandlerNewOnline(getApplicationContext(), response, details).handleKOT();
                            }
                            else if ("online_booking".equalsIgnoreCase(type)) {
                                new BookingPrintHandler(getApplicationContext(), response).handleBookingPrint();
                            }
                            else if ("online_invoice".equalsIgnoreCase(type)) {
                                Object detailsObj = response.get("details");  // ✅ Can be JSONObject or JSONArray
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    JSONObject settings = response.has("settings") ? response.getJSONObject("settings") : new JSONObject();

                                    PayableHandler payableHandler;

                                    // ✅ Pass the correct type to the constructor
                                    if (detailsObj instanceof JSONObject) {
                                        payableHandler = new PayableHandler(this, payData, outlets, (JSONObject) detailsObj, printerIP, printerPort, type, restSettings, params);
                                    } else if (detailsObj instanceof JSONArray) {
                                        payableHandler = new PayableHandler(this, payData, outlets, (JSONArray) detailsObj, printerIP, printerPort, type, restSettings, params, settings);
                                    } else {
                                        throw new JSONException("Invalid type for details: must be JSONObject or JSONArray");
                                    }

                                    byte[] formattedBytes = payableHandler.formatOnlinePayableBytes();

                                    int invoicePrintCopies = response.getJSONArray("printsettings")
                                            .getJSONObject(0)
                                            .optInt("invoice_print_copies", 1); // default 1

                                    //   TODO _NEW invoice print count based on api
                                    for (int i = 0; i < invoicePrintCopies; i++) {
                                        PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                        printConnection.execute();
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }

                            else if ("invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type, restSettings, params);

                                    byte[] formattedBytes = payableHandler.formatPayableBytes(); // Now returns byte[]
                                    int invoicePrintCopies = response.getJSONArray("printsettings")
                                            .getJSONObject(0)
                                            .optInt("invoice_print_copies", 1); // default 1

                                    //   TODO _NEW invoice print count based on api
                                    for (int i = 0; i < invoicePrintCopies; i++) {
                                        PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                        printConnection.execute();
                                    }

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }

                            else if ("equal_split_payable_invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type, restSettings, params);

                                    byte[] formattedBytes = payableHandler.formatPayableSplitBytes(); // Now returns byte[]
                                    PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                    printConnection.execute();

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }

                            else if ("Invoice_split_item".equalsIgnoreCase(type)) {
                                JSONObject valueDetails = response.getJSONObject("value_details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    JSONObject restSettings = response.has("rest_settings")
                                            ? response.getJSONObject("rest_settings")
                                            : new JSONObject();

                                    // Loop through each split invoice
                                    Iterator<String> keys = valueDetails.keys();
                                    while (keys.hasNext()) {
                                        String key = keys.next();
                                        JSONObject splitObj = valueDetails.getJSONObject(key);
                                        JSONObject payData = splitObj.getJSONObject("data");
                                        JSONObject details = splitObj.getJSONObject("details");

                                        PayableHandler payableHandler = new PayableHandler(
                                                this,
                                                payData,
                                                outlets,
                                                details,
                                                printerIP,
                                                printerPort,
                                                type,
                                                restSettings,
                                                params
                                        );

                                        byte[] formattedBytes = payableHandler.formatPayableSplitBytes();
                                        PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                        printConnection.execute();

                                        // Optional: small delay between prints to avoid printer buffer issues
                                        Thread.sleep(300);
                                    }
                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }

                            else if ("Before_Invoice".equalsIgnoreCase(type)) {
                                JSONObject details = response.getJSONObject("details");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;
                                String formattedText;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    // Loop through printers to find matching ID
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);

                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {

                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type, restSettings, params);
//                                    PayableHandler payableHandler = new PayableHandler(this, payData, outlets, details, printerIP, printerPort, type);
                                    byte[] formattedBytes = payableHandler.formatPayableBytes(); // Now returns byte[]
                                    PrintConnection_PAY printConnection = new PrintConnection_PAY(printerIP, printerPort, formattedBytes);
                                    printConnection.execute();

                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }

                            }else if ("petty_cash".equalsIgnoreCase(type)) {

                                JSONObject details = new JSONObject(); // ✅ Fix: dummy empty object

                                JSONArray printers = response.getJSONArray("printers");
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);

                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        int printerId = printer.getInt("id");

                                        if (printerId == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    JSONObject restSettings = response.has("rest_settings") ? response.getJSONObject("rest_settings") : new JSONObject();
                                    PettyCashHandler pettyCashHandler = new PettyCashHandler(
                                            this, payData, outlets, details, printerIP, printerPort, type, restSettings, params
                                    );

                                    String formattedText = pettyCashHandler.formatPettyCashPrint(response); // ⬅ Make sure to pass full response or adjust inside
                                    PrintConnection printConnection = new PrintConnection(printerIP, printerPort, formattedText);
                                    printConnection.execute();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found in printersetup.");
                                }
                            }else if ("petty_cash_invoice".equalsIgnoreCase(type)) {
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray outlets = response.getJSONArray("outlets");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");
                                JSONObject pettyCashData = response.getJSONObject("data");
                                JSONObject restSettings = response.optJSONObject("rest_settings");

                                String printerIP = "";
                                int printerPort = 9100;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    PettyCashHandler pettyCashHandler = new PettyCashHandler(this, pettyCashData, outlets, null, printerIP, printerPort, type, restSettings, params);
                                    String formattedBytes = pettyCashHandler.formatTodayPettyCashPrint(pettyCashData);
                                    new PrintConnection(printerIP, printerPort, formattedBytes).execute();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found for print_today_petty_cash.");
                                }
                            }else if ("mainsaway".equalsIgnoreCase(type)) {
                                JSONObject payData = response.getJSONObject("data");
                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                String printerIP = "";
                                int printerPort = 9100;

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                if (!printerIP.isEmpty()) {
                                    new MainsAwayHandler(this, payData, printerIP, printerPort).printMainsAway();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found for mainsaway.");
                                }
                            }

// Reports
                            else if("daily_summary_report".equalsIgnoreCase(type)){

                                String printerIP = "";
                                int printerPort = 9100;

                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                String printData = ReportHandler.buildDailySummaryReport(response, "daily_summary_report");

                                if (!printerIP.isEmpty()) {
                                    PrintConnection printConnection = new PrintConnection(printerIP, printerPort, printData);
                                    printConnection.execute();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found for mainsaway.");
                                }

                            }

                            else if("online_report".equalsIgnoreCase(type) || "offline_report".equalsIgnoreCase(type) || "booking_report".equalsIgnoreCase(type)) {

                                String printerIP = "";
                                int printerPort = 9100;

                                JSONArray printers = response.getJSONArray("printers");
                                JSONArray printerSetupArray = response.getJSONArray("printersetup");

                                if (printerSetupArray.length() > 0) {
                                    int printerIdToUse = printerSetupArray.getInt(0);
                                    for (int i = 0; i < printers.length(); i++) {
                                        JSONObject printer = printers.getJSONObject(i);
                                        if (printer.getInt("id") == printerIdToUse) {
                                            printerIP = printer.optString("ip", "");
                                            printerPort = Integer.parseInt(printer.optString("port", "9100"));
                                            break;
                                        }
                                    }
                                }

                                String printData = ReportHandler.buildOnlineReport(response, type);

                                if (!printerIP.isEmpty()) {
                                    PrintConnection printConnection = new PrintConnection(printerIP, printerPort, printData);
                                    printConnection.execute();
                                } else {
                                    Log.e("PrintError", "No valid printer IP found for online_report.");
                                }
                            }

                            else {
                                Log.w("PrintService", "Type not supported: " + type);
                            }
                        } catch (Exception e) {
                            Log.e("PrintService", "JSON error", e);
                        }
                    },
                    error -> Log.e("PrintService", "Volley error", error)
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String,String> h = new HashMap<>();
                    h.put("X-API-KEY", "zF3warIELPw61WV4V722hU4l63y752Al");
                    return h;
                }
            };

            request.setRetryPolicy(new DefaultRetryPolicy(
                    10000, // 10 seconds timeout
                    3,     // max retries
                    1.5f   // backoff multiplier
            ));
            queue.add(request);
        } catch (Exception e) {
            Log.e("PrintService", "Error handling deep link", e);
        }
    }
}
