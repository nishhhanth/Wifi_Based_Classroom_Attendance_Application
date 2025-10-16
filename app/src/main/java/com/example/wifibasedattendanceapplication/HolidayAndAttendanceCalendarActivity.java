package com.example.wifibasedattendanceapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Simple screen with two toggles: ATTENDANCE and HOLIDAY.
 * - HOLIDAY loads Indian public holidays from Google's public iCal feed and lists them per selected day.
 * - ATTENDANCE shows present/absent for the student per day based on Firebase data.
 */
public class HolidayAndAttendanceCalendarActivity extends AppCompatActivity {

    private static final String TAG = "CalendarScreen";
    private static final String INDIA_HOLIDAY_ICS = "https://calendar.google.com/calendar/ical/en.indian%23holiday%40group.v.calendar.google.com/public/basic.ics";

    private TextView btnAttendance;
    private TextView btnHoliday;
    private LinearLayout attendanceContainer;
    private LinearLayout holidayContainer;

    private CalendarView attendanceCalendar;
    private CalendarView holidayCalendar;
    // Removed legacy text view for attendance info; using RecyclerView instead
    private ProgressBar progressHoliday;
    private androidx.recyclerview.widget.RecyclerView rvAttendance;
    private androidx.recyclerview.widget.RecyclerView rvHoliday;
    private AttendanceEntryAdapter adapter;
    private HolidayEntryAdapter holidayAdapter;

    private String currentStudentEnrollment;
    private DatabaseReference studentsRef;
    private DatabaseReference reportsRef;

    // Holidays mapped by yyyy-MM-dd -> list of titles
    private final Map<String, List<String>> dateToHolidays = new HashMap<>();
    // Attendance mapped by yyyy-MM-dd -> list of "Subject - Status" lines
    private final Map<String, List<String>> dateToAttendanceDetails = new HashMap<>();

    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_holiday_attendance_calendar);

        initViews();
        resolveCurrentStudent();

        // Load holidays immediately
        fetchIndianPublicHolidays();
    }

    private void initViews() {
        btnAttendance = findViewById(R.id.btn_tab_attendance);
        btnHoliday = findViewById(R.id.btn_tab_holiday);
        attendanceContainer = findViewById(R.id.container_attendance);
        holidayContainer = findViewById(R.id.container_holiday);
        attendanceCalendar = findViewById(R.id.calendar_attendance);
        holidayCalendar = findViewById(R.id.calendar_holiday);
        rvAttendance = findViewById(R.id.rv_attendance_entries);
        rvHoliday = findViewById(R.id.rv_holiday_entries);
        progressHoliday = findViewById(R.id.progress_holiday);

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        reportsRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");

        adapter = new AttendanceEntryAdapter();
        rvAttendance.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvAttendance.setAdapter(adapter);

        holidayAdapter = new HolidayEntryAdapter();
        rvHoliday.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvHoliday.setAdapter(holidayAdapter);

        btnAttendance.setOnClickListener(v -> {
            // Sync selected date from holiday calendar to attendance calendar
            try { attendanceCalendar.setDate(holidayCalendar.getDate(), false, true); } catch (Exception ignored) {}
            showAttendanceTab();
            updateAttendanceInfoFromMillis(attendanceCalendar.getDate());
        });
        btnHoliday.setOnClickListener(v -> {
            // Sync selected date from attendance calendar to holiday calendar
            try { holidayCalendar.setDate(attendanceCalendar.getDate(), false, true); } catch (Exception ignored) {}
            showHolidayTab();
            updateHolidayInfoFromMillis(holidayCalendar.getDate());
        });

        attendanceCalendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            updateAttendanceInfo(year, month, dayOfMonth);
            // Keep calendars in sync
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            try { holidayCalendar.setDate(cal.getTimeInMillis(), false, true); } catch (Exception ignored) {}
        });
        holidayCalendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            updateHolidayInfo(year, month, dayOfMonth);
            // Keep calendars in sync
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            try { attendanceCalendar.setDate(cal.getTimeInMillis(), false, true); } catch (Exception ignored) {}
        });

        // Default to Attendance tab
        showAttendanceTab();
        updateAttendanceInfoFromMillis(attendanceCalendar.getDate());
    }

    private void showAttendanceTab() {
        attendanceContainer.setVisibility(View.VISIBLE);
        holidayContainer.setVisibility(View.GONE);
        btnAttendance.setSelected(true);
        btnHoliday.setSelected(false);
    }

    private void showHolidayTab() {
        holidayContainer.setVisibility(View.VISIBLE);
        attendanceContainer.setVisibility(View.GONE);
        btnHoliday.setSelected(true);
        btnAttendance.setSelected(false);
    }

    private void resolveCurrentStudent() {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        // Also accept enrollment via Intent extra
        String enrollmentFromIntent = getIntent() != null ? getIntent().getStringExtra("studentEnrollment") : null;
        if (enrollmentFromIntent != null && !enrollmentFromIntent.trim().isEmpty()) {
            currentStudentEnrollment = enrollmentFromIntent;
            loadAttendanceForStudent(currentStudentEnrollment);
            // Also perform a direct report scan to ensure data appears even if the student's map is stale
            scanReportsForStudent(currentStudentEnrollment);
            return;
        }
        if (userEmail == null) {
            // Fallback: pick first student in database
            studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot s : dataSnapshot.getChildren()) {
                        currentStudentEnrollment = s.getKey();
                        break;
                    }
                    if (currentStudentEnrollment != null) {
                        loadAttendanceForStudent(currentStudentEnrollment);
                        scanReportsForStudent(currentStudentEnrollment);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            });
            return;
        }

        studentsRef.orderByChild("student_email").equalTo(userEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                                currentStudentEnrollment = studentSnapshot.getKey();
                                loadAttendanceForStudent(currentStudentEnrollment);
                                scanReportsForStudent(currentStudentEnrollment);
                                break;
                            }
                        } else {
                            // Fallback: pick first student if email match not found
                            studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    for (DataSnapshot s : snapshot.getChildren()) {
                                        currentStudentEnrollment = s.getKey();
                                        break;
                                    }
                                    if (currentStudentEnrollment != null) {
                                        loadAttendanceForStudent(currentStudentEnrollment);
                                        scanReportsForStudent(currentStudentEnrollment);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) { }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void loadAttendanceForStudent(String enrollment) {
        // Read the student's Attendance map: sessionId -> P/A
        studentsRef.child(enrollment).child("Attendance")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            // Fallback: scan AttendanceReport for sessions containing this student
                            scanReportsForStudent(enrollment);
                            return;
                        }

                        List<String> sessionIds = new ArrayList<>();
                        Map<String, String> sessionToCode = new HashMap<>();
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String sessionId = s.getKey();
                            String code = s.getValue(String.class);
                            if (sessionId == null || code == null) continue;
                            sessionIds.add(sessionId);
                            sessionToCode.put(sessionId, code);
                        }
                        if (sessionIds.isEmpty()) {
                            // As a fallback, still scan reports in case the mapping hasn't been written yet
                            scanReportsForStudent(enrollment);
                            return;
                        }

                        // For each session, fetch period_date and subject to map to a day with details
                        for (String sessionId : sessionIds) {
                            reportsRef.child(sessionId).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot rep) {
                                    String periodDate = rep.child("period_date").getValue(String.class); // expected dd-MM-yyyy or similar
                                    String subject = rep.child("subject").getValue(String.class);
                                    if (periodDate != null) {
                                        String normalized = normalizeToDateKey(periodDate);
                                        if (normalized != null) {
                                            String code = sessionToCode.get(sessionId);
                                            String mapped = mapAttendanceCode(code);
                                            // Fallback to session-level status if needed
                                            if (mapped == null || "Not Marked".equalsIgnoreCase(mapped)) {
                                                String sessionStatusText = rep.child("Students").child(enrollment).child("attendance_status").getValue(String.class);
                                                if (sessionStatusText != null && !sessionStatusText.trim().isEmpty()) {
                                                    mapped = sessionStatusText;
                                                }
                                            }

                                            if (mapped == null) mapped = "Not Marked";

                                            List<String> lines = dateToAttendanceDetails.get(normalized);
                                            if (lines == null) {
                                                lines = new ArrayList<>();
                                                dateToAttendanceDetails.put(normalized, lines);
                                            }
                                            String sub = (subject == null || subject.isEmpty()) ? "Session" : subject;
                                            String endTime = formatSessionEndTime(rep);
                                            if (endTime != null && !endTime.isEmpty()) {
                                                lines.add(sub + " - " + mapped + " - " + endTime);
                                            } else {
                                                lines.add(sub + " - " + mapped);
                                            }
                                            // Update UI after each entry
                                            updateAttendanceInfoFromMillis(attendanceCalendar.getDate());
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) { }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void scanReportsForStudent(String enrollment) {
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot all) {
                for (DataSnapshot rep : all.getChildren()) {
                    // Only include sessions for the same division if known to reduce noise
                    try {
                        String div = rep.child("division").getValue(String.class);
                        // If student's division is present in Students tree, filter; else accept all
                    } catch (Exception ignored) {}
                    if (rep.child("Students").child(enrollment).exists()) {
                        String sid = rep.getKey();
                        String code = rep.child("Students").child(enrollment).child("attendance_status").getValue(String.class);
                        String periodDate = rep.child("period_date").getValue(String.class);
                        String subject = rep.child("subject").getValue(String.class);
                        String normalized = periodDate == null ? null : normalizeToDateKey(periodDate);
                        if (normalized == null) continue;
                        String mapped = mapAttendanceCode(code);
                        if (mapped == null) mapped = (code == null ? "Not Marked" : code);
                        List<String> lines = dateToAttendanceDetails.get(normalized);
                        if (lines == null) {
                            lines = new ArrayList<>();
                            dateToAttendanceDetails.put(normalized, lines);
                        }
                        String sub = (subject == null || subject.isEmpty()) ? "Session" : subject;
                        String endTime = formatSessionEndTime(rep);
                        if (endTime != null && !endTime.isEmpty()) {
                            lines.add(sub + " - " + mapped + " - " + endTime);
                        } else {
                            lines.add(sub + " - " + mapped);
                        }
                    }
                }
                updateAttendanceInfoFromMillis(attendanceCalendar.getDate());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void fetchIndianPublicHolidays() {
        progressHoliday.setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    URL url = new URL(INDIA_HOLIDAY_ICS);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestMethod("GET");
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    br.close();
                    return sb.toString();
                } catch (Exception e) {
                    Log.e(TAG, "Holiday fetch failed: " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String ics) {
                progressHoliday.setVisibility(View.GONE);
                if (ics == null) return;
                parseIcsAndPopulate(ics);
                updateHolidayInfoFromMillis(holidayCalendar.getDate());
            }
        }.execute();
    }

    private void parseIcsAndPopulate(String ics) {
        // Minimal iCal parsing: look for DTSTART, SUMMARY pairs inside VEVENT
        // DTSTART can be in UTC or local; most Google holiday DTSTART are date-only (YYYYMMDD)
        String[] lines = ics.split("\n");
        String currentDate = null;
        String currentSummary = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("BEGIN:VEVENT")) {
                currentDate = null;
                currentSummary = null;
            } else if (line.startsWith("DTSTART")) {
                String value = line.substring(line.indexOf(':') + 1).trim();
                String key = icsDateToKey(value);
                currentDate = key;
            } else if (line.startsWith("SUMMARY:")) {
                currentSummary = line.substring("SUMMARY:".length()).trim();
            } else if (line.startsWith("END:VEVENT")) {
                if (currentDate != null && currentSummary != null) {
                    List<String> list = dateToHolidays.get(currentDate);
                    if (list == null) {
                        list = new ArrayList<>();
                        dateToHolidays.put(currentDate, list);
                    }
                    list.add(currentSummary);
                }
            }
        }
    }

    private void updateHolidayInfo(int year, int zeroBasedMonth, int day) {
        // CalendarView months are 0-based
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(year, zeroBasedMonth, day, 0, 0, 0);
        updateHolidayInfoFromMillis(cal.getTimeInMillis());
    }

    private void updateHolidayInfoFromMillis(long millis) {
        dateKeyFormat.setTimeZone(TimeZone.getDefault());
        displayDateFormat.setTimeZone(TimeZone.getDefault());
        String key = dateKeyFormat.format(new Date(millis));
        List<String> titles = dateToHolidays.get(key);
        String displayDate = displayDateFormat.format(new Date(millis));
        
        // Convert holiday titles to adapter entries
        java.util.List<HolidayEntryAdapter.Entry> entries = new java.util.ArrayList<>();
        if (titles != null && !titles.isEmpty()) {
            for (String title : titles) {
                entries.add(new HolidayEntryAdapter.Entry(title, displayDate, "Public Holiday"));
            }
        }
        holidayAdapter.setEntries(entries);
    }

    private void updateAttendanceInfo(int year, int zeroBasedMonth, int day) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(year, zeroBasedMonth, day, 0, 0, 0);
        updateAttendanceInfoFromMillis(cal.getTimeInMillis());
    }

    private void updateAttendanceInfoFromMillis(long millis) {
        dateKeyFormat.setTimeZone(TimeZone.getDefault());
        displayDateFormat.setTimeZone(TimeZone.getDefault());
        String key = dateKeyFormat.format(new Date(millis));
        // Always (re)build from AttendanceReport so we can sort by time reliably
        buildAndDisplayEntriesForDate(key, millis);
    }

    private void buildAndDisplayEntriesForDate(@NonNull String dateKey, long millis) {
        if (currentStudentEnrollment == null) {
            adapter.setEntries(new java.util.ArrayList<>());
            return;
        }
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot all) {
                class TempEntry { String subject; String status; String time; long ts; }
                java.util.List<TempEntry> temp = new java.util.ArrayList<>();

                for (DataSnapshot rep : all.getChildren()) {
                    if (!rep.child("Students").child(currentStudentEnrollment).exists()) continue;
                    String pd = rep.child("period_date").getValue(String.class);
                    String normalized = pd == null ? null : normalizeToDateKey(pd);
                    if (normalized == null || !normalized.equals(dateKey)) continue;

                    String subject = rep.child("subject").getValue(String.class);
                    String code = rep.child("Students").child(currentStudentEnrollment).child("attendance_status").getValue(String.class);
                    String status = mapAttendanceCode(code);
                    if (status == null) status = (code == null ? "Not Marked" : code);

                    Long endTs = rep.child("end_timestamp").getValue(Long.class);
                    String endTimeStr = formatSessionEndTime(rep);
                    long ts = endTs != null ? endTs : parseTimeToMillis(dateKey, endTimeStr);

                    TempEntry e = new TempEntry();
                    e.subject = (subject == null || subject.isEmpty()) ? "Session" : subject;
                    e.status = status;
                    e.time = endTimeStr == null ? "" : endTimeStr;
                    e.ts = ts;
                    temp.add(e);
                }

                temp.sort((a, b) -> Long.compare(a.ts, b.ts));

                java.util.List<AttendanceEntryAdapter.Entry> entries = new java.util.ArrayList<>();
                for (TempEntry t : temp) {
                    entries.add(new AttendanceEntryAdapter.Entry(t.subject, t.status, t.time));
                }
                adapter.setEntries(entries);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                adapter.setEntries(new java.util.ArrayList<>());
            }
        });
    }

    private long parseTimeToMillis(@NonNull String dateKey, String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0L;
        try {
            // dateKey is yyyy-MM-dd
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.US);
            df.setTimeZone(TimeZone.getDefault());
            Date d = df.parse(dateKey + " " + timeStr);
            return d == null ? 0L : d.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void tryLoadSelectedDateOnDemand(@NonNull String dateKey, long millis) {
        if (currentStudentEnrollment == null) return;
        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot all) {
                List<String> lines = dateToAttendanceDetails.get(dateKey);
                if (lines == null) {
                    lines = new ArrayList<>();
                    dateToAttendanceDetails.put(dateKey, lines);
                }
                int before = lines.size();
                for (DataSnapshot rep : all.getChildren()) {
                    if (!rep.child("Students").child(currentStudentEnrollment).exists()) continue;
                    String pd = rep.child("period_date").getValue(String.class);
                    String normalized = pd == null ? null : normalizeToDateKey(pd);
                    if (normalized == null || !normalized.equals(dateKey)) continue;
                    String subject = rep.child("subject").getValue(String.class);
                    String code = rep.child("Students").child(currentStudentEnrollment).child("attendance_status").getValue(String.class);
                    String mapped = mapAttendanceCode(code);
                    if (mapped == null) mapped = (code == null ? "Not Marked" : code);
                    String endTime = formatSessionEndTime(rep);
                    String sub = (subject == null || subject.isEmpty()) ? "Session" : subject;
                    if (endTime != null && !endTime.isEmpty()) {
                        lines.add(sub + " - " + mapped + " - " + endTime);
                    } else {
                        lines.add(sub + " - " + mapped);
                    }
                }
                if (lines.size() != before) {
                    updateAttendanceInfoFromMillis(millis);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private String icsDateToKey(String icsDate) {
        // Handles YYYYMMDD or YYYYMMDDT000000Z
        try {
            if (icsDate == null || icsDate.length() < 8) return null;
            String y = icsDate.substring(0, 4);
            String m = icsDate.substring(4, 6);
            String d = icsDate.substring(6, 8);
            return y + "-" + m + "-" + d;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeToDateKey(String raw) {
        // Accept common formats like dd-MM-yyyy, dd/MM/yyyy, yyyy-MM-dd
        String[] patterns = new String[]{
                "dd/MM/yy", "d/M/yy",
                "dd/MM/yyyy", "d/M/yyyy",
                "dd-MM-yy", "d-M-yy",
                "dd-MM-yyyy", "d-M-yyyy",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                Date dt = f.parse(raw);
                if (dt != null) return dateKeyFormat.format(dt);
            } catch (ParseException ignored) { }
        }
        return null;
    }

    private String formatSessionEndTime(@NonNull DataSnapshot rep) {
        try {
            Long ts = rep.child("end_timestamp").getValue(Long.class);
            if (ts != null && ts > 0) {
                return timeFormat.format(new Date(ts));
            }
        } catch (Exception ignored) { }
        try {
            String end = rep.child("end_time").getValue(String.class);
            return end == null ? null : end;
        } catch (Exception e) {
            return null;
        }
    }

    private String mapAttendanceCode(String code) {
        if (code == null) return null;
        if ("P".equalsIgnoreCase(code)) return "Present";
        if ("A".equalsIgnoreCase(code)) return "Absent";
        return code;
    }
}


