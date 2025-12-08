package com.example.posprint;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public class KOTHandler {

    Context context;
    JSONObject response, details;

    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 35;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_RESET = "\u001B" + "!" + (char) 0;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    public KOTHandler(Context context, JSONObject response, JSONObject details) {
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
        StringBuilder formattedText = new StringBuilder();

        try {
            JSONObject orderDetails = objectDetails.getJSONObject("order_details");

            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("KOT :Kitchen", true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n")
                    .append("-".repeat(45))
                    .append("\n");

            String type = orderDetails.optString("order_type", "");
            String orderNo = orderDetails.optString("order_no", "");
            String orderTime = orderDetails.optString("order_time", "");
            String waiter = orderDetails.optString("waiter_name", "");
            String customer = orderDetails.optString("customer_name", "");
            String tableno = orderDetails.optString("tableno", "");
            int seats = orderDetails.optInt("table_seats", 0);

            formattedText.append("\nDate: ").append(orderTime)
                    .append("\nCustomer: ").append(customer)
                    .append("\nServed by: ").append(waiter);

            if (type.equals("dinein")) {
                if (!tableno.equals("null") && !tableno.isEmpty()) {
                    formattedText.append("\n")
                            .append(ESC_FONT_SIZE_LARGE)
                            .append("Table: ").append(tableno)
                            .append(ESC_FONT_SIZE_RESET)
                            .append("\nSeats: ").append(seats);
                } else {
                    formattedText.append("\nTable: -\nSeats: -");
                }
            }

            formattedText.append("\n\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(type.toUpperCase() + " #" + orderNo, true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n\n")
                    .append("-".repeat(45))
                    .append("\n");

            JSONObject categories = objectDetails.getJSONObject("categories");
            for (Iterator<String> catIterator = categories.keys(); catIterator.hasNext(); ) {
                String category = catIterator.next();
                JSONObject items = categories.getJSONObject(category);

                formattedText.append(ESC_FONT_SIZE_MEDIUM)
                        .append(centerText(category, true))
                        .append(ESC_FONT_SIZE_RESET).append("\n");

                formattedText.append("-".repeat(45)).append("\n");

                for (Iterator<String> itemIterator = items.keys(); itemIterator.hasNext(); ) {
                    String itemId = itemIterator.next();
                    JSONObject item = items.getJSONObject(itemId);

                    // ⭐ SPECIAL CASE: BANQUET NIGHTS
                    if (category.equalsIgnoreCase("BANQUET NIGHTS")) {

                        Object addonObj = item.opt("addon");

                        if (addonObj instanceof JSONObject) {
                            JSONObject addonGroups = (JSONObject) addonObj;

                            for (Iterator<String> groupIterator = addonGroups.keys(); groupIterator.hasNext();) {
                                String groupName = groupIterator.next();
                                JSONObject groupItems = addonGroups.optJSONObject(groupName);

                                if (groupItems != null && groupItems.length() > 0) {

                                    // ⭐ Group title
                                    formattedText.append(ESC_FONT_SIZE_MEDIUM)
                                            .append("\n").append(groupName)
                                            .append(ESC_FONT_SIZE_RESET)
                                            .append("\n");

                                    // ⭐ Sub-items under group
                                    for (Iterator<String> subItemIterator = groupItems.keys(); subItemIterator.hasNext();) {
                                        String subItemKey = subItemIterator.next();
                                        JSONObject subAddon = groupItems.getJSONObject(subItemKey);
                                        String adName = subAddon.optString("ad_name");
                                        String adQty = subAddon.optString("ad_qty");

                                        formattedText.append(
                                                ESC_FONT_SIZE_MEDIUM +
                                                        "  " + adQty + "x " + adName +
                                                        ESC_FONT_SIZE_RESET + "\n"
                                        );
                                    }
                                }
                            }
                        }

                        // NOTE section
                        String other = item.optString("other");
                        if (other != null && !other.trim().isEmpty()) {
                            formattedText.append("\nNote: ").append(other).append("\n");
                        }
                    }

                    // ⭐ NORMAL CATEGORIES
                    else {

                        String itemName = item.optString("item");
                        String qty = item.optString("quantity");

                        Object addonObj = item.opt("addon");

                        // ⭐ CASE 1: Addons exist → print addon first, then main item
                        if (addonObj instanceof JSONObject && ((JSONObject) addonObj).length() > 0) {

                            JSONObject addons = (JSONObject) addonObj;

                            // ⭐ 1️⃣ PRINT ADDONS FIRST
                            for (Iterator<String> addonIterator = addons.keys(); addonIterator.hasNext();) {
                                String addonKey = addonIterator.next();
                                JSONObject addonItem = addons.getJSONObject(addonKey);

                                String adName = addonItem.optString("ad_name");
                                String adQty = addonItem.optString("ad_qty");

                                formattedText.append(
                                        ESC_FONT_SIZE_MEDIUM +
                                                adQty + "x " + adName +
                                                ESC_FONT_SIZE_RESET +
                                                "\n"
                                );
                            }

                            // ⭐ 2️⃣ PRINT MAIN ITEM BELOW ADDON
                            formattedText.append(
                                    ESC_FONT_SIZE_MEDIUM +
                                            "   " + qty + "x " + itemName +
                                            ESC_FONT_SIZE_RESET +
                                            "\n"
                            );
                        }
                        else {
                            // ⭐ CASE 2: No addon → normal item print
                            formattedText.append(
                                    ESC_FONT_SIZE_MEDIUM +
                                            qty + "x " + itemName +
                                            ESC_FONT_SIZE_RESET +
                                            "\n"
                            );
                        }

                        // ⭐ Print Note if exists
                        String other = item.optString("other");
                        if (other != null && !other.trim().isEmpty()) {
                            formattedText.append("Note: ").append(other).append("\n");
                        }
                    }

                    formattedText.append("\n");
                }

                formattedText.append("-".repeat(45)).append("\n");
            }

            // Footer
            formattedText.append("Special Instruction: ")
                    .append(orderDetails.optString("instruction")).append("\n")
                    .append("-".repeat(45)).append("\n");

            String deliveryTime = orderDetails.optString("deliverytime", "");

            if (deliveryTime != null &&
                    !deliveryTime.trim().isEmpty() &&
                    !deliveryTime.equalsIgnoreCase("null")) {

                formattedText.append(ESC_FONT_SIZE_MEDIUM)
                        .append("Requested for: ")
                        .append(deliveryTime)
                        .append(ESC_FONT_SIZE_RESET)
                        .append("\n")
                        .append("-".repeat(45))
                        .append("\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error formatting KOT text", e);
        }

        return formattedText.toString();
    }


    /*private String formatKOTText(JSONObject response, JSONObject objectDetails) {
        StringBuilder formattedText = new StringBuilder();

        try {
            JSONObject orderDetails = objectDetails.getJSONObject("order_details");

            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("KOT :Kitchen", true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n")
                    .append("-".repeat(45))
                    .append("\n");

            String type = orderDetails.optString("order_type", "");
            String orderNo = orderDetails.optString("order_no", "");
            String orderTime = orderDetails.optString("order_time", "");
            String waiter = orderDetails.optString("waiter_name", "");
            String customer = orderDetails.optString("customer_name", "");
            String tableno = orderDetails.optString("tableno", "");
            int seats = orderDetails.optInt("table_seats", 0);

            formattedText.append("\nDate: ").append(orderTime)
                    .append("\nCustomer: ").append(customer)
                    .append("\nServed by: ").append(waiter);

            if (type.equals("dinein")) {
                if (!tableno.equals("null") && !tableno.isEmpty()) {
                    formattedText.append("\n")
                            .append(ESC_FONT_SIZE_LARGE)
                            .append("Table: ").append(tableno)
                            .append(ESC_FONT_SIZE_RESET)
                            .append("\nSeats: ").append(seats);
                } else {
                    formattedText.append("\nTable: -\nSeats: -");
                }
            }

            formattedText.append("\n\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(type.toUpperCase() + " #" + orderNo, true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n\n")
                    .append("-".repeat(45))
                    .append("\n");

            JSONObject categories = objectDetails.getJSONObject("categories");
            for (Iterator<String> catIterator = categories.keys(); catIterator.hasNext(); ) {
                String category = catIterator.next();
                JSONObject items = categories.getJSONObject(category);

                formattedText.append(ESC_FONT_SIZE_MEDIUM)
                        .append(centerText(category, true))
                        .append(ESC_FONT_SIZE_RESET).append("\n");

                formattedText.append("-".repeat(45)).append("\n");

                for (Iterator<String> itemIterator = items.keys(); itemIterator.hasNext(); ) {
                    String itemId = itemIterator.next();
                    JSONObject item = items.getJSONObject(itemId);

                    // For "BANQUET NIGHTS", print addons directly
                    if (category.equalsIgnoreCase("BANQUET NIGHTS")) {

                        // Addon is expected to be a nested JSONObject with categories
                        Object addonObj = item.opt("addon");
                        if (addonObj instanceof JSONObject) {
                            JSONObject addonGroups = (JSONObject) addonObj;
                            for (Iterator<String> groupIterator = addonGroups.keys(); groupIterator.hasNext(); ) {
                                String groupName = groupIterator.next();
                                JSONObject groupItems = addonGroups.optJSONObject(groupName);
                                if (groupItems != null && groupItems.length() > 0) {
                                    // Group title
                                    formattedText.append(ESC_FONT_SIZE_MEDIUM).append("\n").append(groupName).append(ESC_FONT_SIZE_RESET).append("\n");
                                    for (Iterator<String> subItemIterator = groupItems.keys(); subItemIterator.hasNext(); ) {
                                        String subItemKey = subItemIterator.next();
                                        JSONObject subAddon = groupItems.getJSONObject(subItemKey);
                                        String adName = subAddon.optString("ad_name");
                                        String adQty = subAddon.optString("ad_qty");
                                        formattedText.append(ESC_FONT_SIZE_MEDIUM).append("\n  ").append(adQty).append(" x ").append(adName).append(ESC_FONT_SIZE_RESET).append("\n");
                                    }
                                }
                            }
                        }

                        // Print 'other' if available
                        String other = item.optString("other");
                        if (other != null && !other.trim().isEmpty()) {
                            formattedText.append("\n").append("Note: ").append(other).append("\n");
//                            formattedText.append(ESC_FONT_SIZE_NOTE).append("\n").append("Note: ").append(other).append(ESC_FONT_SIZE_RESET).append("\n");
                        }
                    }
                    else {

                        String itemName = item.optString("item");
                        String qty = item.optString("quantity");

                        Object addonObj = item.opt("addon");

                        // ⭐ CASE 1: Addons exist → print addon first, then main item
                        if (addonObj instanceof JSONObject && ((JSONObject) addonObj).length() > 0) {

                            JSONObject addons = (JSONObject) addonObj;

                            // ⭐ 1️⃣ PRINT ADDONS FIRST
                            for (Iterator<String> addonIterator = addons.keys(); addonIterator.hasNext();) {
                                String addonKey = addonIterator.next();
                                JSONObject addonItem = addons.getJSONObject(addonKey);

                                String adName = addonItem.optString("ad_name");
                                String adQty = addonItem.optString("ad_qty");

                                formattedText.append(
                                        ESC_FONT_SIZE_MEDIUM +
                                                adQty + " x " + adName +
                                                ESC_FONT_SIZE_RESET +
                                                "\n"
                                );
                            }

                            // ⭐ 2️⃣ PRINT MAIN ITEM BELOW ADDON
                            formattedText.append(
                                    ESC_FONT_SIZE_MEDIUM +
                                            "   " + qty + " x " + itemName +
                                            ESC_FONT_SIZE_RESET +
                                            "\n"
                            );
                        }
                        else {
                            // ⭐ CASE 2: No addon → normal item print
                            formattedText.append(
                                    ESC_FONT_SIZE_MEDIUM +
                                            qty + " x " + itemName +
                                            ESC_FONT_SIZE_RESET +
                                            "\n"
                            );
                        }

                        // ⭐ Print Note if exists
                        String other = item.optString("other");
                        if (other != null && !other.trim().isEmpty()) {
                            formattedText.append("Note: ").append(other).append("\n");
                        }
                    }

                    formattedText.append("\n");
                }

                formattedText*//*.append("\n")*//*.append("-".repeat(45)).append("\n");
            }

            formattedText.append("Special Instruction: ")
                    .append(orderDetails.optString("instruction")).append("\n")
                    .append("-".repeat(45)).append("\n");

            String deliveryTime = orderDetails.optString("deliverytime", "");

            if (deliveryTime != null &&
                    !deliveryTime.trim().isEmpty() &&
                    !deliveryTime.equalsIgnoreCase("null")) {

                formattedText.append(ESC_FONT_SIZE_MEDIUM)
                        .append("Requested for: ")
                        .append(deliveryTime)
                        .append(ESC_FONT_SIZE_RESET)
                        .append("\n")
                        .append("-".repeat(45))
                        .append("\n");

            }

        } catch (Exception e) {
            Log.e(TAG, "Error formatting KOT text", e);
        }

        return formattedText.toString();
    }*/

    // ----------------------------------------------------------------------
    private String centerText(String text, boolean dbl) {
        int width = 45;
        int len = dbl ? text.length() * 2 : text.length();
        int spaces = (width - len) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }

    private JSONObject getPrinterDetails(int printerId, JSONArray printersArray) {
        for (int i = 0; i < printersArray.length(); i++) {
            try {
                JSONObject printer = printersArray.getJSONObject(i);
                if (printer.getInt("id") == printerId) return printer;
            } catch (Exception ignore) {}
        }
        return null;
    }
}