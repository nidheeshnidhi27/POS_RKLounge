package com.example.posprint;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class KOTHandlerNewOnline_old_working {
    Context context;
    JSONObject response, details;
    private static final String ESC_FONT_SIZE_LARGE = "\u001B" + "!" + (char) 51;
    private static final String ESC_FONT_SIZE_MEDIUM = "\u001B" + "!" + (char) 35;
    private static final String ESC_FONT_SIZE_SMALL = "\u001B" + "!" + (char) 23;
    private static final String ESC_FONT_SIZE_RESET = "\u001B" + "!" + (char) 0;

    public KOTHandlerNewOnline_old_working(Context context, JSONObject response, JSONObject details) {

        this.context = context;
        this.response = response;
        this.details = details;
    }

    public void handleKOT() {
        try {
            // Loop through each detail object (e.g., "1", "6")
            for (Iterator<String> keyIterator = details.keys(); keyIterator.hasNext(); ) {
                String key = keyIterator.next();
                JSONObject objectDetails = details.getJSONObject(key);
                // Get the printer ID and fetch printer details
//                int printerId = objectDetails.getInt("printer");

                Object printerObj = objectDetails.get("printer");
                int printerId = -1;

                if (printerObj instanceof JSONArray) {
                    JSONArray printerArray = (JSONArray) printerObj;
                    if (printerArray.length() > 0) {
                        printerId = printerArray.optInt(0, -1);
                    }
                } else if (printerObj instanceof Number) {
                    printerId = ((Number) printerObj).intValue();
                } else {
                    Log.e(TAG, "Unexpected printer type: " + printerObj);
                    continue; // Skip faulty data
                }

                if (printerId == -1) {
                    Log.e(TAG, "Printer ID invalid!");
                    continue;
                }

                JSONObject printerDetails = getPrinterDetails(printerId, response.getJSONArray("printers"));
                if (printerDetails == null) {
                    Log.e(TAG, "Printer details not found for printer ID: " + printerId);
                    continue; // Skip this object if printer details are missing
                }
                // Extract printer IP and port
                String printerIP = printerDetails.optString("ip");
                int printerPort = Integer.parseInt(printerDetails.optString("port", "9100")); // Default to 9100 if port is not found
                // Format the text for the current detail object
                String formattedText = formatOnlineKOTText(response, objectDetails);

                int kotPrintCopies = response.getJSONArray("printsettings")
                        .getJSONObject(0)
                        .optInt("kot_print_copies", 1); // default 1

                // 🔥 Execute printing multiple times
                for (int i = 0; i < kotPrintCopies; i++) {
                    PrintConnection printConnection = new PrintConnection(printerIP, printerPort, formattedText);
                    printConnection.execute();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling KOT", e);
        }
    }

    private String formatOnlineKOTText(JSONObject response, JSONObject objectDetails) {
        StringBuilder formattedText = new StringBuilder();
        try {
            JSONObject orderDetails = objectDetails.getJSONObject("order_details");
            formattedText.append(ESC_FONT_SIZE_LARGE)
                    .append(centerText("KOT :Kitchen", true))
                    .append(ESC_FONT_SIZE_RESET).append("\n");
            formattedText.append("-".repeat(45)).append("\n");

            String type = orderDetails.optString("order_type", "");
            String orderNo = orderDetails.optString("id", "");
            String orderTime = orderDetails.optString("order_time", "");
            String waiter = orderDetails.optString("waiter_name", "");
            String customer = orderDetails.optString("customer_name", "");
            String address = orderDetails.optString("customer_address", "");
            String phone = orderDetails.optString("customer_phone", "");
            String tableno = orderDetails.optString("tableno", "");
            int tableSeats = orderDetails.optInt("table_seats", 0);

            String displayType;
            switch (type) {
                case "dinein": displayType = "Dine-In"; break;
                case "takeaway": displayType = "Takeaway"; break;
                case "delivery": displayType = "Delivery"; break;
                default: displayType = type; break;
            }

            formattedText.append("\nDate: ").append(orderTime);

            if (customer.isEmpty()) {
                String firstName = orderDetails.optString("firstname", "").trim();
                String lastName = orderDetails.optString("lastname", "").trim();

                // Combine firstname and lastname (with space if both exist)
                customer = (firstName + " " + lastName).trim();
            }

// Append to print text
            formattedText.append("\nCustomer: ").append(customer);

            if (type.equals("delivery")) {
                if (!TextUtils.isEmpty(address)) formattedText.append("\nAddress: ").append(address);
                if (!TextUtils.isEmpty(phone)) formattedText.append("\nPhone: ").append(phone);
            }

            if (!waiter.isEmpty()) {
                formattedText.append("\nServed by: ").append(waiter);
            }
            if (type.equals("dinein")) {

                if (tableno != null &&
                        !tableno.trim().isEmpty() &&
                        !tableno.equalsIgnoreCase("null")) {

                    formattedText.append("\n")
                            .append(ESC_FONT_SIZE_LARGE)
                            .append("Table: " ).append(tableno)
                            .append(ESC_FONT_SIZE_RESET);
                    formattedText.append("\nSeats: ").append(tableSeats);
                } else {
                    formattedText.append("\nTable: -");
                    formattedText.append("\nSeats: -");
                }

                /*formattedText.append("\n").append(ESC_FONT_SIZE_LARGE).append(tableno).append(ESC_FONT_SIZE_RESET);
                formattedText.append("\nSeats: ").append(tableSeats);*/
            }

            formattedText.append("\n\n")
                    .append(ESC_FONT_SIZE_LARGE)
                    .append(centerText(displayType + " #" + orderNo, true))
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n\n");

            formattedText.append("-".repeat(45)).append("\n");

            // ✅ Handle Both JSONObject and JSONArray types
            Object categoriesObj = objectDetails.opt("categories");

//            Object categoriesObj = objectDetails.opt("categories");

            if (categoriesObj instanceof JSONObject) {
                JSONObject categories = (JSONObject) categoriesObj;

                for (Iterator<String> catIterator = categories.keys(); catIterator.hasNext(); ) {
                    String category = catIterator.next();
                    Object itemObj = categories.get(category);

                    formattedText.append(ESC_FONT_SIZE_MEDIUM)
                            .append(centerText(category, true))
                            .append(ESC_FONT_SIZE_RESET).append("\n")
                            .append("-".repeat(45)).append("\n");

                    // If it's a JSONObject, iterate by keys
                    if (itemObj instanceof JSONObject) {
                        JSONObject items = (JSONObject) itemObj;

                        for (Iterator<String> itemIterator = items.keys(); itemIterator.hasNext(); ) {
                            String itemId = itemIterator.next();
                            JSONObject item = items.getJSONObject(itemId);
                            formatItemBlock(item, category, formattedText);
                        }

                        // If it's a JSONArray, iterate directly
                    } else if (itemObj instanceof JSONArray) {
                        JSONArray itemsArray = (JSONArray) itemObj;

                        for (int j = 0; j < itemsArray.length(); j++) {
                            JSONObject item = itemsArray.getJSONObject(j);
                            formatItemBlock(item, category, formattedText);
                        }
                    }

                    formattedText/*.append("\n")*/.append("-".repeat(45)).append("\n");
                }
            }
//            formattedText/*.append("\n")*/.append("-".repeat(45)).append("\n");
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

    private void formatItemBlock(JSONObject item, String category, StringBuilder formattedText) throws JSONException {

        String qty = item.optString("quantity");
        String name = item.optString("item");

        Object addonObj = item.opt("addon");

        // -----------------------------
        // ⭐ CASE 1: BANQUET NIGHTS OR GROUPED ADDON
        // -----------------------------
        if (category.equalsIgnoreCase("BANQUET NIGHTS")) {

            if (addonObj instanceof JSONObject) {
                JSONObject addonGroups = (JSONObject) addonObj;

                for (Iterator<String> groupIterator = addonGroups.keys(); groupIterator.hasNext();) {
                    String groupName = groupIterator.next();
                    JSONObject groupItems = addonGroups.optJSONObject(groupName);

                    if (groupItems != null && groupItems.length() > 0) {

                        // Group name
                        formattedText.append(ESC_FONT_SIZE_MEDIUM)
                                .append("\n").append(groupName)
                                .append(ESC_FONT_SIZE_RESET)
                                .append("\n");

                        // Sub items
                        for (Iterator<String> subItemIterator = groupItems.keys(); subItemIterator.hasNext();) {
                            String subItemKey = subItemIterator.next();
                            JSONObject subAddon = groupItems.optJSONObject(subItemKey);

                            if (subAddon != null) {
                                printAddonItem(subAddon, formattedText);
                            }
                        }
                    }
                }
            }

            // BANQUET → print NOTE only (no main item)
            String other = item.optString("other");
            if (other != null && !other.trim().isEmpty()) {
                formattedText.append("Note: ").append(other).append("\n");
            }

            formattedText.append("\n");
            return;
        }

        // -----------------------------
        // ⭐ CASE 2: NORMAL CATEGORY
        // -----------------------------
        boolean addonFound = false;

        // --------------------------------
        // 1️⃣ PRINT ADDON FIRST (NO SPACE)
        // --------------------------------
        if (addonObj instanceof JSONArray) {

            JSONArray addonArray = (JSONArray) addonObj;

            for (int i = 0; i < addonArray.length(); i++) {

                JSONObject addonItem = addonArray.optJSONObject(i);

                if (addonItem == null) continue;

                String adName = addonItem.optString("ad_name", "").trim();
                String adQty  = addonItem.optString("ad_qty", "0").trim();

                if (!adName.isEmpty() && !adQty.equals("0")) {

                    addonFound = true;

                    formattedText.append(ESC_FONT_SIZE_MEDIUM)
                            .append(adQty).append("x ").append(adName)  // NO SPACES
                            .append(ESC_FONT_SIZE_RESET)
                            .append("\n");
                }
            }
        }

        // -------------------------------
        // 2️⃣ PRINT MAIN ITEM (3 SPACES)
        // -------------------------------
        if (addonFound) {
            formattedText.append(ESC_FONT_SIZE_MEDIUM)
                    .append("   ")   // 3 spaces only when addon exists
                    .append(qty).append("x ").append(name)
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n");
        } else {
            // No addon → normal printing
            formattedText.append(ESC_FONT_SIZE_MEDIUM)
                    .append(qty).append("x ").append(name)
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n");
        }

        // -------------------------------
        // 3️⃣ PRINT NOTE (IF ANY)
        // -------------------------------
        String other = item.optString("other", "").trim();
        if (!other.isEmpty()) {
            formattedText.append("Note: ").append(other).append("\n");
        }

        formattedText.append("\n");
    }

    private void printAddonItem(JSONObject addonItem, StringBuilder formattedText) {

        String adName = addonItem.optString("ad_name", "").trim();
        String adQty  = addonItem.optString("ad_qty", "0").trim();

        if (!adName.isEmpty() && !adQty.equals("0")) {
            formattedText.append(ESC_FONT_SIZE_MEDIUM)
                    .append("   ")          // 3 spaces indent for addon
                    .append(adQty).append("x ").append(adName)
                    .append(ESC_FONT_SIZE_RESET)
                    .append("\n");
        }
    }





    private String centerText(String text, boolean isDoubleWidth) {
        int fullLineWidth = 45;
        int visualTextLength = isDoubleWidth ? text.length() * 2 : text.length();
        int spaces = (fullLineWidth - visualTextLength) / 2;
        if (spaces < 0) spaces = 0;
        return " ".repeat(spaces) + text;
    }
    private JSONObject getPrinterDetails(int printerId, JSONArray printersArray) {
        for (int i = 0; i < printersArray.length(); i++) {
            try {
                JSONObject printer = printersArray.getJSONObject(i);
                if (printer.getInt("id") == printerId) {
                    return printer;
                }
            } catch (JSONException e) {
                Log.e("API", "Error fetching printer details", e);
            }
        }
        return null; // If no matching printer is found
    }

    // SAME AS KOTHandler – NO PRICE SHOWN
    private String formatItemLine(String qty, String name) {

        int lineWidth = 30; // 58mm paper
        String firstLineLeft = qty + "x " + name;

        // Full in one line
        if (firstLineLeft.length() <= lineWidth) {
            return firstLineLeft;
        }

        // WRAP long names
        int maxChars = lineWidth;
        StringBuilder out = new StringBuilder();

        String firstLine = firstLineLeft.substring(0, maxChars);
        out.append(firstLine);

        String remaining = firstLineLeft.substring(maxChars).trim();

        while (remaining.length() > maxChars) {
            out.append("\n    ")
                    .append(remaining.substring(0, maxChars));
            remaining = remaining.substring(maxChars).trim();
        }

        if (!remaining.isEmpty()) {
            out.append("\n    ").append(remaining);
        }

        return out.toString();
    }

}