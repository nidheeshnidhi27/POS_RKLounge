package com.example.posprint;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public class KOTHandler_without_category {

    Context context;
    JSONObject response, details;

    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 35;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_RESET = "\u001B" + "!" + (char) 0;

    public KOTHandler_without_category(Context context, JSONObject response, JSONObject details) {
        this.context = context;
        this.response = response;
        this.details = details;
    }

    public void handleKOT() {
        try {
            for (Iterator<String> keyIterator = details.keys(); keyIterator.hasNext(); ) {

                String key = keyIterator.next();
                JSONObject objectDetails = details.getJSONObject(key);

                Object printerObj = objectDetails.get("printer");
                int printerId = -1;

                if (printerObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) printerObj;
                    if (arr.length() > 0) printerId = arr.optInt(0, -1);
                } else if (printerObj instanceof Number) {
                    printerId = ((Number) printerObj).intValue();
                }

                if (printerId == -1) continue;

                JSONObject printerDetails =
                        getPrinterDetails(printerId, response.getJSONArray("printers"));

                if (printerDetails == null) continue;

                String printerIP = printerDetails.optString("ip");
                int printerPort = Integer.parseInt(printerDetails.optString("port", "9100"));

                String formattedText = formatKOTText(response, objectDetails);

                int copies = response.getJSONArray("printsettings")
                        .getJSONObject(0)
                        .optInt("kot_print_copies", 1);

                for (int i = 0; i < copies; i++) {
                    PrintConnection connection = new PrintConnection(printerIP, printerPort, formattedText);
                    connection.execute();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "ERROR IN KOT", e);
        }
    }

    private String formatKOTText(JSONObject response, JSONObject objectDetails) {
        StringBuilder out = new StringBuilder();

        try {
            JSONObject order = objectDetails.getJSONObject("order_details");

            out.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("KOT :Kitchen",true))
                    .append(ESC_RESET).append("\n");

            out.append("---------------------------------------------\n");

            out.append("Date: ").append(order.optString("order_time")).append("\n");
            out.append("Customer: ").append(order.optString("customer_name")).append("\n");
            out.append("Served by: ").append(order.optString("waiter_name")).append("\n");

            String type = order.optString("order_type");
            String orderNo = order.optString("order_no");

            out.append("\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(type.toUpperCase() + " #" + orderNo, true))
                    .append(ESC_RESET).append("\n\n");

            out.append("---------------------------------------------\n");

            JSONObject categories = objectDetails.getJSONObject("categories");

            for (Iterator<String> cat = categories.keys(); cat.hasNext(); ) {

                JSONObject items = categories.getJSONObject(cat.next());

                for (Iterator<String> itemIterator = items.keys(); itemIterator.hasNext(); ) {

                    String itemId = itemIterator.next();
                    JSONObject item = items.getJSONObject(itemId);

                    String itemName = item.optString("item");
                    double qty = item.optDouble("quantity", 1);
                    double price = item.optDouble("amount", 0);

                    if (price == 0) {

                        Object addonObj = item.opt("addon");

                        if (addonObj instanceof JSONObject) {
                            JSONObject addons = (JSONObject) addonObj;

                            // Take FIRST addon’s `ad_price`
                            for (Iterator<String> addonIt = addons.keys(); addonIt.hasNext(); ) {

                                JSONObject addItem = addons.getJSONObject(addonIt.next());

                                double adPrice = addItem.optDouble("ad_price", 0);

                                if (adPrice > 0) {
                                    price = adPrice * qty;
                                }

                                break; // only first addon needed
                            }
                        }

                    }

                    String line = formatItemLine(String.valueOf((int) qty), itemName, qty * price);

                    out.append(ESC_FONT_SIZE_MEDIUM)
                            .append(line)
                            .append(ESC_RESET)
                            .append("\n");

                    // Addons
                    Object addonObj = item.opt("addon");
                    if (addonObj instanceof JSONObject) {
                        JSONObject addons = (JSONObject) addonObj;

                        for (Iterator<String> addonIt = addons.keys(); addonIt.hasNext(); ) {

                            JSONObject addItem = addons.getJSONObject(addonIt.next());
                            out.append(ESC_FONT_SIZE_MEDIUM)
                                    .append("  ")
                                    .append(addItem.optString("ad_qty"))
                                    .append("x ")
                                    .append(addItem.optString("ad_name"))
                                    .append(ESC_RESET)
                                    .append("\n");
                        }
                    }

                    // Other note
                    String other = item.optString("other", "");
                    if (!other.trim().isEmpty()) {
                        out.append("Note: ").append(other).append("\n");
                    }

                    out.append("\n");
                }
            }

            out.append("---------------------------------------------\n");

            out.append("Special Instruction: ")
                    .append(order.optString("instruction"))
                    .append("\n");

            out.append("---------------------------------------------\n");

            String dTime = order.optString("delivery_time");
            if (!TextUtils.isEmpty(dTime) && !"null".equalsIgnoreCase(dTime)) {
                out.append("Requested for: ").append(dTime).append("\n");
                out.append("---------------------------------------------\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "FORMAT ERROR", e);
        }

        return out.toString();
    }

    private String formatItemLine(String qty, String name, double totalIgnored) {

        int lineWidth = 30; // KOT width for item text only
        String firstLineLeft = qty + "x " + name;

        // If fits in one line
        if (firstLineLeft.length() <= lineWidth) {
            return firstLineLeft;
        }

        // WRAP long item name properly
        int maxChars = lineWidth;
        StringBuilder out = new StringBuilder();

        // First line
        String firstLineText = firstLineLeft.substring(0, maxChars);
        out.append(firstLineText);

        // Remaining text to next lines
        String remaining = firstLineLeft.substring(maxChars).trim();

        while (remaining.length() > maxChars) {
            out.append("\n    ")                      // indent
                    .append(remaining.substring(0, maxChars));
            remaining = remaining.substring(maxChars).trim();
        }

        // Last remaining line
        if (!remaining.isEmpty()) {
            out.append("\n    ").append(remaining);
        }

        return out.toString();
    }


    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }

    private JSONObject getPrinterDetails(int id, JSONArray printers) {
        for (int i = 0; i < printers.length(); i++) {
            try {
                JSONObject obj = printers.getJSONObject(i);
                if (obj.getInt("id") == id) return obj;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
