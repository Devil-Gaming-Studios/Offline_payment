package com.example.offlinewallet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Local-only wallet ledger. Balance and transaction history live in
// SharedPreferences on the device. Also holds a queue of completed
// two-way-QR transactions that haven't been reported to the server's
// /qrsync/report endpoint yet — MainActivity drains this queue whenever
// it gets a chance to reach the server.
public class WalletStore {

    private static final String PREFS_NAME = "wallet_prefs";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_PENDING_SYNC = "pending_sync";
    private static final int MAX_HISTORY_ITEMS = 50;

    private final SharedPreferences prefs;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

    public WalletStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public double getBalance() {
        return Double.longBitsToDouble(prefs.getLong(KEY_BALANCE, Double.doubleToLongBits(0)));
    }

    public void credit(double amount) {
        setBalance(getBalance() + amount);
    }

    public void debit(double amount) {
        setBalance(getBalance() - amount);
    }

    private void setBalance(double amount) {
        prefs.edit()
                .putLong(KEY_BALANCE, Double.doubleToLongBits(amount))
                .apply();
    }

    public void addHistoryEntry(String type, double amount, String counterparty) {
        try {
            JSONArray existing = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));

            JSONObject entry = new JSONObject();
            entry.put("type", type);
            entry.put("amount", amount);
            entry.put("counterparty", counterparty);
            entry.put("time", System.currentTimeMillis());

            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < existing.length() && i < MAX_HISTORY_ITEMS - 1; i++) {
                updated.put(existing.get(i));
            }

            prefs.edit().putString(KEY_HISTORY, updated.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public List<String> getHistory() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String type = entry.getString("type");
                double amount = entry.getDouble("amount");
                String counterparty = entry.getString("counterparty");
                long time = entry.getLong("time");

                String sign = type.equals("Sent") ? "-" : "+";
                result.add(String.format(Locale.getDefault(), "%s   %s\u20B9%.2f   \u2022   %s   \u2022   %s",
                        type, sign, amount, counterparty, timeFormat.format(new Date(time))));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    // ---------- Pending server sync queue (for /qrsync/report) ----------

    // Queues a completed two-way-QR transaction for later reporting to the
    // server. Called on BOTH sides (payer after finalizeOutgoingPayment,
    // payee after acceptScannedTransaction) — the server dedupes by nonce
    // regardless of which side (or both) eventually reports it.
    public void queuePendingSync(byte[] senderPublicKey, byte[] receiverPublicKey,
                                  long amountPaise, long timestamp, String nonce,
                                  byte[] senderSignature, byte[] receiverSignature) {
        try {
            JSONArray queue = new JSONArray(prefs.getString(KEY_PENDING_SYNC, "[]"));

            JSONObject entry = new JSONObject();
            entry.put("senderPublicKey", android.util.Base64.encodeToString(senderPublicKey, android.util.Base64.NO_WRAP));
            entry.put("receiverPublicKey", android.util.Base64.encodeToString(receiverPublicKey, android.util.Base64.NO_WRAP));
            entry.put("amountPaise", amountPaise);
            entry.put("timestamp", timestamp);
            entry.put("nonce", nonce);
            entry.put("senderSignature", android.util.Base64.encodeToString(senderSignature, android.util.Base64.NO_WRAP));
            entry.put("receiverSignature", android.util.Base64.encodeToString(receiverSignature, android.util.Base64.NO_WRAP));

            queue.put(entry);
            prefs.edit().putString(KEY_PENDING_SYNC, queue.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public List<JSONObject> getPendingSync() {
        List<JSONObject> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_PENDING_SYNC, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getJSONObject(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    // Removes one entry by nonce once the server has confirmed it (RECORDED
    // or DUPLICATE_IGNORED both mean it's safely accounted for server-side).
    public void removePendingSync(String nonce) {
        try {
            JSONArray existing = new JSONArray(prefs.getString(KEY_PENDING_SYNC, "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject entry = existing.getJSONObject(i);
                if (!entry.getString("nonce").equals(nonce)) {
                    updated.put(entry);
                }
            }
            prefs.edit().putString(KEY_PENDING_SYNC, updated.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public int getPendingSyncCount() {
        return getPendingSync().size();
    }
}
