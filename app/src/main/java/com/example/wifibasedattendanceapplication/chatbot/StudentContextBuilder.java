package com.example.wifibasedattendanceapplication.chatbot;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds context information about the student for the chatbot
 */
public class StudentContextBuilder {
    
    private static final String TAG = "StudentContextBuilder";
    
    private DatabaseReference studentsRef;
    private DatabaseReference reportsRef;
    private String currentStudentEnrollment;
    
    public interface ContextCallback {
        void onContextReady(String context);
        void onError(String error);
    }
    
    public StudentContextBuilder() {
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        reportsRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");
    }
    
    public void buildStudentContext(ContextCallback callback) {
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                          FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        
        if (userEmail == null) {
            callback.onError("User not authenticated");
            return;
        }
        
        // Find student by email
        studentsRef.orderByChild("student_email").equalTo(userEmail)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        for (DataSnapshot studentSnapshot : dataSnapshot.getChildren()) {
                            currentStudentEnrollment = studentSnapshot.getKey();
                            buildContextFromStudentData(studentSnapshot, callback);
                            return;
                        }
                    }
                    callback.onError("Student not found");
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    callback.onError("Database error: " + error.getMessage());
                }
            });
    }
    
    private void buildContextFromStudentData(DataSnapshot studentSnapshot, ContextCallback callback) {
        StringBuilder context = new StringBuilder();
        
        // Basic student information
        String studentName = studentSnapshot.child("student_name").getValue(String.class);
        String division = studentSnapshot.child("Division").getValue(String.class);
        String branch = studentSnapshot.child("Branch").getValue(String.class);
        String enrollment = studentSnapshot.getKey();
        
        context.append("STUDENT PROFILE:\n");
        context.append("Name: ").append(studentName != null ? studentName : "N/A").append("\n");
        context.append("Enrollment: ").append(enrollment != null ? enrollment : "N/A").append("\n");
        context.append("Division: ").append(division != null ? division : "N/A").append("\n");
        context.append("Branch: ").append(branch != null ? branch : "N/A").append("\n");
        context.append("Academic Year: 2024-2025\n\n");
        
        // Get attendance data
        loadAttendanceData(context, callback);
    }
    
    private void loadAttendanceData(StringBuilder context, ContextCallback callback) {
        if (currentStudentEnrollment == null) {
            context.append("ATTENDANCE: No data available\n\n");
            loadFeesData(context, callback);
            return;
        }
        
        studentsRef.child(currentStudentEnrollment).child("Attendance")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot attendanceSnapshot) {
                    if (attendanceSnapshot.exists()) {
                        calculateAttendanceStats(attendanceSnapshot, context, callback);
                    } else {
                        context.append("ATTENDANCE: No attendance records found\n\n");
                        loadFeesData(context, callback);
                    }
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    context.append("ATTENDANCE: Error loading data\n\n");
                    loadFeesData(context, callback);
                }
            });
    }
    
    private void calculateAttendanceStats(DataSnapshot attendanceSnapshot, StringBuilder context, ContextCallback callback) {
        final int[] totalSessions = {0};
        final int[] presentSessions = {0};
        final Map<String, int[]> subjectStats = new HashMap<>(); // subject -> [present, total]
        final Map<String, List<AttendanceRecord>> dailyAttendance = new HashMap<>(); // date -> list of records
        
        List<String> sessionIds = new ArrayList<>();
        for (DataSnapshot sessionSnapshot : attendanceSnapshot.getChildren()) {
            sessionIds.add(sessionSnapshot.getKey());
        }
        
        if (sessionIds.isEmpty()) {
            context.append("ATTENDANCE: No sessions found\n\n");
            loadFeesData(context, callback);
            return;
        }
        
        final int totalToFetch = sessionIds.size();
        final int[] fetched = new int[]{0};
        
        for (DataSnapshot sessionSnapshot : attendanceSnapshot.getChildren()) {
            String sessionId = sessionSnapshot.getKey();
            String attendanceStatus = sessionSnapshot.getValue(String.class);
            
            // Get complete session details from AttendanceReport
            reportsRef.child(sessionId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot sessionDetailsSnapshot) {
                        if (sessionDetailsSnapshot.exists()) {
                            String subject = sessionDetailsSnapshot.child("subject").getValue(String.class);
                            String periodDate = sessionDetailsSnapshot.child("period_date").getValue(String.class);
                            String startTime = sessionDetailsSnapshot.child("start_time").getValue(String.class);
                            String endTime = sessionDetailsSnapshot.child("end_time").getValue(String.class);
                            
                            if (subject != null && !subject.isEmpty()) {
                                totalSessions[0]++;
                                
                                // Update subject stats
                                int[] stats = subjectStats.get(subject);
                                if (stats == null) {
                                    stats = new int[]{0, 0};
                                    subjectStats.put(subject, stats);
                                }
                                stats[1]++; // total
                                
                                boolean isPresent = "P".equalsIgnoreCase(attendanceStatus) || "Present".equalsIgnoreCase(attendanceStatus);
                                if (isPresent) {
                                    presentSessions[0]++;
                                    stats[0]++; // present
                                }
                                
                                // Store daily attendance record
                                if (periodDate != null) {
                                    AttendanceRecord record = new AttendanceRecord();
                                    record.subject = subject;
                                    record.date = periodDate;
                                    record.startTime = startTime;
                                    record.endTime = endTime;
                                    record.status = isPresent ? "Present" : "Absent";
                                    record.sessionId = sessionId;
                                    
                                    // Store in both formats for better matching
                                    String displayDate = convertDateFormat(periodDate);
                                    List<AttendanceRecord> dayRecords = dailyAttendance.get(periodDate);
                                    if (dayRecords == null) {
                                        dayRecords = new ArrayList<>();
                                        dailyAttendance.put(periodDate, dayRecords);
                                    }
                                    dayRecords.add(record);
                                    
                                    // Also store with display format for better AI matching
                                    List<AttendanceRecord> displayDayRecords = dailyAttendance.get(displayDate);
                                    if (displayDayRecords == null) {
                                        displayDayRecords = new ArrayList<>();
                                        dailyAttendance.put(displayDate, displayDayRecords);
                                    }
                                    displayDayRecords.add(record);
                                }
                            }
                        }
                        
                        fetched[0]++;
                        if (fetched[0] == totalToFetch) {
                            formatAttendanceContext(context, totalSessions[0], presentSessions[0], subjectStats, dailyAttendance);
                            loadFeesData(context, callback);
                        }
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        fetched[0]++;
                        if (fetched[0] == totalToFetch) {
                            formatAttendanceContext(context, totalSessions[0], presentSessions[0], subjectStats, dailyAttendance);
                            loadFeesData(context, callback);
                        }
                    }
                });
        }
    }
    
    // Helper class to store attendance records
    private static class AttendanceRecord {
        String subject;
        String date;
        String startTime;
        String endTime;
        String status;
        String sessionId;
    }
    
    private void formatAttendanceContext(StringBuilder context, int totalSessions, int presentSessions, Map<String, int[]> subjectStats, Map<String, List<AttendanceRecord>> dailyAttendance) {
        context.append("ATTENDANCE OVERVIEW:\n");
        
        if (totalSessions > 0) {
            double percentage = (double) presentSessions / totalSessions * 100;
            context.append("Overall Attendance: ").append(String.format("%.1f", percentage)).append("% (")
                   .append(presentSessions).append("/").append(totalSessions).append(")\n");
            
            // Calculate classes needed for 75%
            int classesNeededFor75 = calculateClassesNeededFor75(presentSessions, totalSessions);
            if (classesNeededFor75 > 0) {
                context.append("Classes needed for 75%: ").append(classesNeededFor75).append(" more classes\n");
            } else if (percentage >= 75.0) {
                context.append("You have already achieved 75% attendance! Great job!\n");
            }
            
            if (!subjectStats.isEmpty()) {
                context.append("\nSubject-wise Attendance:\n");
                for (Map.Entry<String, int[]> entry : subjectStats.entrySet()) {
                    String subject = entry.getKey();
                    int[] stats = entry.getValue();
                    double subjectPercentage = stats[1] > 0 ? (double) stats[0] / stats[1] * 100 : 0;
                    context.append("- ").append(subject).append(": ").append(String.format("%.1f", subjectPercentage))
                           .append("% (").append(stats[0]).append("/").append(stats[1]).append(")\n");
                    
                    // Calculate classes needed for 75% for this subject
                    int subjectClassesNeeded = calculateClassesNeededFor75(stats[0], stats[1]);
                    if (subjectClassesNeeded > 0) {
                        context.append("  Classes needed for 75%: ").append(subjectClassesNeeded).append(" more classes\n");
                    } else if (subjectPercentage >= 75.0) {
                        context.append("  ✓ Already achieved 75% attendance\n");
                    }
                }
            }
            
            // Add recent daily attendance (last 10 days)
            if (!dailyAttendance.isEmpty()) {
                context.append("\nRecent Daily Attendance:\n");
                List<String> sortedDates = new ArrayList<>(dailyAttendance.keySet());
                sortedDates.sort((a, b) -> {
                    try {
                        // Try both date formats for sorting
                        SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                        SimpleDateFormat format2 = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                        Date dateA = null, dateB = null;
                        
                        try {
                            dateA = format1.parse(a);
                        } catch (Exception e1) {
                            try {
                                dateA = format2.parse(a);
                            } catch (Exception e2) {
                                // Keep as null
                            }
                        }
                        
                        try {
                            dateB = format1.parse(b);
                        } catch (Exception e1) {
                            try {
                                dateB = format2.parse(b);
                            } catch (Exception e2) {
                                // Keep as null
                            }
                        }
                        
                        if (dateA != null && dateB != null) {
                            return dateB.compareTo(dateA); // Sort descending (newest first)
                        }
                        return b.compareTo(a); // Fallback to string comparison
                    } catch (Exception e) {
                        return b.compareTo(a); // Fallback to string comparison
                    }
                });
                
                int count = 0;
                Set<String> processedDates = new HashSet<>(); // To avoid duplicates
                
                for (String date : sortedDates) {
                    if (count >= 10) break; // Limit to last 10 days
                    
                    List<AttendanceRecord> dayRecords = dailyAttendance.get(date);
                    if (dayRecords != null && !dayRecords.isEmpty()) {
                        // Check if we've already processed this date (avoid duplicates from both formats)
                        String normalizedDate = date.contains("/") ? convertDateFormat(date) : date;
                        if (processedDates.contains(normalizedDate)) {
                            continue;
                        }
                        processedDates.add(normalizedDate);
                        
                        context.append("- ").append(normalizedDate).append(":\n");
                        for (AttendanceRecord record : dayRecords) {
                            context.append("  • ").append(record.subject).append(" (")
                                   .append(record.startTime).append(" - ").append(record.endTime)
                                   .append("): ").append(record.status).append("\n");
                        }
                        count++;
                    }
                }
            }
        } else {
            context.append("No valid attendance records found\n");
        }
        context.append("\n");
    }
    
    private int calculateClassesNeededFor75(int present, int total) {
        if (total == 0) return 0;
        
        double currentPercentage = (double) present / total * 100;
        if (currentPercentage >= 75.0) return 0;
        
        // Calculate how many more classes needed for 75%
        // 75% = present / (total + x) * 100
        // 0.75 = present / (total + x)
        // 0.75 * (total + x) = present
        // 0.75 * total + 0.75 * x = present
        // 0.75 * x = present - 0.75 * total
        // x = (present - 0.75 * total) / 0.75
        
        double needed = (present - 0.75 * total) / 0.75;
        return Math.max(0, (int) Math.ceil(-needed)); // Ceil and ensure non-negative
    }
    
    private void loadFeesData(StringBuilder context, ContextCallback callback) {
        if (currentStudentEnrollment == null) {
            context.append("FEES: No data available\n\n");
            addAppFeatures(context);
            callback.onContextReady(context.toString());
            return;
        }
        
        studentsRef.child(currentStudentEnrollment).child("Fees")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot feesSnapshot) {
                    if (feesSnapshot.exists()) {
                        String feeStatus = feesSnapshot.child("fee_status").getValue(String.class);
                        Integer totalFees = feesSnapshot.child("total_fees").getValue(Integer.class);
                        Integer paidFees = feesSnapshot.child("paid_fees").getValue(Integer.class);
                        Integer remainingFees = feesSnapshot.child("remaining_fees").getValue(Integer.class);
                        
                        context.append("FEES STATUS:\n");
                        context.append("Status: ").append(feeStatus != null ? feeStatus : "N/A").append("\n");
                        if (totalFees != null) {
                            context.append("Total Fees: ₹").append(totalFees).append("\n");
                        }
                        if (paidFees != null) {
                            context.append("Paid: ₹").append(paidFees).append("\n");
                        }
                        if (remainingFees != null) {
                            context.append("Remaining: ₹").append(remainingFees).append("\n");
                        }
                    } else {
                        context.append("FEES: No fees data available\n");
                    }
                    context.append("\n");
                    
                    loadCalendarData(context, callback);
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    context.append("FEES: Error loading data\n\n");
                    loadCalendarData(context, callback);
                }
            });
    }
    
    private void loadCalendarData(StringBuilder context, ContextCallback callback) {
        context.append("CALENDAR & HOLIDAYS:\n");
        
        // Get current date and upcoming holidays
        Calendar calendar = Calendar.getInstance();
        Date today = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        
        context.append("Today: ").append(dateFormat.format(today)).append(" (").append(dayFormat.format(today)).append(")\n");
        
        // Add upcoming holidays for the next 30 days
        List<String> upcomingHolidays = getUpcomingHolidays(30);
        if (!upcomingHolidays.isEmpty()) {
            context.append("Upcoming Holidays (next 30 days):\n");
            for (String holiday : upcomingHolidays) {
                context.append("- ").append(holiday).append("\n");
            }
        } else {
            context.append("No upcoming holidays in the next 30 days\n");
        }
        
        // Add recent attendance calendar data
        loadRecentAttendanceCalendar(context, callback);
    }
    
    private List<String> getUpcomingHolidays(int days) {
        List<String> holidays = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        
        // Add some common Indian holidays for the next 30 days
        // In a real implementation, you would fetch this from a holiday API or database
        for (int i = 0; i < days; i++) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int month = calendar.get(Calendar.MONTH);
            
            // Add some sample holidays (you can replace with real holiday data)
            if (dayOfWeek == Calendar.SUNDAY) {
                holidays.add(dateFormat.format(calendar.getTime()) + " - Sunday");
            }
            
            // Add some sample holidays for demonstration
            if (month == Calendar.OCTOBER && dayOfMonth == 2) {
                holidays.add(dateFormat.format(calendar.getTime()) + " - Gandhi Jayanti");
            }
            if (month == Calendar.OCTOBER && dayOfMonth == 31) {
                holidays.add(dateFormat.format(calendar.getTime()) + " - Diwali");
            }
            if (month == Calendar.NOVEMBER && dayOfMonth == 1) {
                holidays.add(dateFormat.format(calendar.getTime()) + " - Diwali (Day 2)");
            }
        }
        
        return holidays;
    }
    
    private void loadRecentAttendanceCalendar(StringBuilder context, ContextCallback callback) {
        if (currentStudentEnrollment == null) {
            context.append("Recent Attendance: No data available\n\n");
            addAppFeatures(context);
            callback.onContextReady(context.toString());
            return;
        }
        
        // Get recent attendance data for the last 7 days
        studentsRef.child(currentStudentEnrollment).child("Attendance")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot attendanceSnapshot) {
                    if (attendanceSnapshot.exists()) {
                        context.append("Recent Attendance (Last 7 days):\n");
                        
                        Calendar calendar = Calendar.getInstance();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                        SimpleDateFormat dbDateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                        
                        // Get last 7 days
                        for (int i = 6; i >= 0; i--) {
                            calendar.setTime(new Date());
                            calendar.add(Calendar.DAY_OF_MONTH, -i);
                            Date date = calendar.getTime();
                            String dateKey = dbDateFormat.format(date);
                            
                            // Check if there's attendance data for this date
                            final boolean[] hasAttendance = {false};
                            final List<String> daySubjects = new ArrayList<>();
                            
                            for (DataSnapshot sessionSnapshot : attendanceSnapshot.getChildren()) {
                                String sessionId = sessionSnapshot.getKey();
                                String attendanceStatus = sessionSnapshot.getValue(String.class);
                                
                                // Get session details to check date and subject
                                reportsRef.child(sessionId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot sessionDetailsSnapshot) {
                                            if (sessionDetailsSnapshot.exists()) {
                                                String sessionDate = sessionDetailsSnapshot.child("period_date").getValue(String.class);
                                                String subject = sessionDetailsSnapshot.child("subject").getValue(String.class);
                                                
                                                if (dateKey.equals(sessionDate)) {
                                                    hasAttendance[0] = true;
                                                    String status = "P".equalsIgnoreCase(attendanceStatus) || "Present".equalsIgnoreCase(attendanceStatus) ? "Present" : "Absent";
                                                    daySubjects.add(subject != null ? subject : "Unknown");
                                                    
                                                    // Format the day's attendance
                                                    if (daySubjects.size() == 1) { // First subject for this day
                                                        context.append("- ").append(dateFormat.format(date)).append(" (").append(dayFormat.format(date)).append("):\n");
                                                    }
                                                    context.append("  • ").append(subject != null ? subject : "Unknown").append(": ").append(status).append("\n");
                                                }
                                            }
                                        }
                                        
                                        @Override
                                        public void onCancelled(DatabaseError error) {
                                            // Continue processing other sessions
                                        }
                                    });
                            }
                            
                            if (!hasAttendance[0]) {
                                context.append("- ").append(dateFormat.format(date)).append(" (").append(dayFormat.format(date)).append("): No classes\n");
                            }
                        }
                    } else {
                        context.append("Recent Attendance: No attendance records found\n");
                    }
                    context.append("\n");
                    
                    addAppFeatures(context);
                    callback.onContextReady(context.toString());
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    context.append("Recent Attendance: Error loading data\n\n");
                    addAppFeatures(context);
                    callback.onContextReady(context.toString());
                }
            });
    }
    
    private void addAppFeatures(StringBuilder context) {
        context.append("AVAILABLE APP FEATURES:\n");
        context.append("- Mark Attendance: Submit attendance when connected to WiFi\n");
        context.append("- View Attendance: Check detailed attendance reports\n");
        context.append("- Calendar: View holidays and attendance calendar\n");
        context.append("- Profile: Manage personal information\n");
        context.append("- Fees: Check payment status and due dates\n");
        context.append("- AI Assistant: Get help with any app-related questions\n\n");
        
        context.append("CURRENT DATE: ").append(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date())).append("\n");
        context.append("DATE FORMATS USED:\n");
        context.append("- Database format: dd/MM/yy (e.g., 16/10/24)\n");
        context.append("- Display format: dd MMM yyyy (e.g., 16 Oct 2024)\n");
        context.append("- When looking for specific dates, check both formats\n\n");
    }
    
    // Helper method to convert date formats for better matching
    private String convertDateFormat(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dateStr);
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr; // Return original if conversion fails
        }
    }
}
