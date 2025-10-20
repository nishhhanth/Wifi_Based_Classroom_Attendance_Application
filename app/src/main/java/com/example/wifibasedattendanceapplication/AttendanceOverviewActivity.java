package com.example.wifibasedattendanceapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceOverviewActivity extends AppCompatActivity {

	private static final String TAG = "AttendanceOverview";

    private TextView tvOverallPercent, tvGreetingName;
    private DonutProgressView donutView;
	private RecyclerView recyclerView;
	private ProgressBar progressBar;

	private String currentStudentEnrollment;
	private DatabaseReference studentsRef;
	private DatabaseReference reportsRef;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_attendance_overview);

		initViews();
		resolveCurrentStudent();
	}

	private void initViews() {
        donutView = findViewById(R.id.donut_overall);
		tvGreetingName = findViewById(R.id.tv_greeting_name);
		recyclerView = findViewById(R.id.rv_subjects);
		progressBar = findViewById(R.id.progress);

		recyclerView.setLayoutManager(new LinearLayoutManager(this));

		studentsRef = FirebaseDatabase.getInstance().getReference("Students");
		reportsRef = FirebaseDatabase.getInstance().getReference("AttendanceReport");

		findViewById(R.id.btn_back).setOnClickListener(v -> onBackPressed());
	}

	private void resolveCurrentStudent() {
		String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ?
				FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
		if (userEmail == null) {
			finish();
			return;
		}

		studentsRef.orderByChild("student_email").equalTo(userEmail)
				.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot ds) {
						for (DataSnapshot snap : ds.getChildren()) {
							currentStudentEnrollment = snap.getKey();
							String name = snap.child("student_name").getValue(String.class);
							tvGreetingName.setText(name != null ? name.toUpperCase(Locale.getDefault()) : "STUDENT");
							loadAttendanceBreakdown();
							return;
						}
						finish();
					}

					@Override
					public void onCancelled(@NonNull DatabaseError error) { finish(); }
				});
	}

	private void loadAttendanceBreakdown() {
		if (currentStudentEnrollment == null) return;
		progressBar.setVisibility(View.VISIBLE);

		// 1) Read student's session codes (P/A)
		studentsRef.child(currentStudentEnrollment).child("Attendance")
				.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot attendanceSnap) {
						if (!attendanceSnap.exists()) {
							showData(new ArrayList<>(), 0, 0);
							return;
						}

						// Aggregate per subject
						Map<String, int[]> subjectToCounts = new HashMap<>(); // [present, total]

						final int[] totals = new int[]{0};
						final int[] presents = new int[]{0};

						List<String> sessionIds = new ArrayList<>();
						for (DataSnapshot s : attendanceSnap.getChildren()) {
							sessionIds.add(s.getKey());
						}

						if (sessionIds.isEmpty()) { showData(new ArrayList<>(), 0, 0); return; }

						final int totalToFetch = sessionIds.size();
						final int[] fetched = new int[]{0};

						for (DataSnapshot s : attendanceSnap.getChildren()) {
							String sessionId = s.getKey();
							String code = s.getValue(String.class); // P/A

							reportsRef.child(sessionId).child("subject")
									.addListenerForSingleValueEvent(new ValueEventListener() {
										@Override
										public void onDataChange(@NonNull DataSnapshot subjectSnap) {
											String subject = subjectSnap.getValue(String.class);
											
											// Skip sessions with missing or empty subject data
											if (subject == null || subject.isEmpty()) {
												fetched[0] += 1;
												if (fetched[0] == totalToFetch) {
													showData(buildRows(subjectToCounts), presents[0], totals[0]);
												}
												return;
											}

											int[] counts = subjectToCounts.get(subject);
											if (counts == null) { counts = new int[]{0,0}; subjectToCounts.put(subject, counts); }
											counts[1] += 1; // total per subject
											totals[0] += 1;
											if ("P".equalsIgnoreCase(code) || "Present".equalsIgnoreCase(code)) {
												counts[0] += 1;
												presents[0] += 1;
											}

											fetched[0] += 1;
											if (fetched[0] == totalToFetch) {
												showData(buildRows(subjectToCounts), presents[0], totals[0]);
											}
										}

										@Override
										public void onCancelled(@NonNull DatabaseError error) {
											fetched[0] += 1;
											if (fetched[0] == totalToFetch) {
												showData(buildRows(subjectToCounts), presents[0], totals[0]);
											}
										}
									});
						}
					}

					@Override
					public void onCancelled(@NonNull DatabaseError error) {
						showData(new ArrayList<>(), 0, 0);
					}
				});
	}

    private List<SubjectRow> buildRows(Map<String, int[]> map) {
		List<SubjectRow> rows = new ArrayList<>();
		for (Map.Entry<String, int[]> e : map.entrySet()) {
			String subject = e.getKey();
            int present = e.getValue()[0];
            int total = e.getValue()[1];
            double pct = total == 0 ? 0.0 : (present * 100.0 / total);
            rows.add(new SubjectRow(subject, total, present, (int)Math.round(pct)));
		}
		return rows;
	}

	private void showData(List<SubjectRow> rows, int present, int total) {
		progressBar.setVisibility(View.GONE);
        double overall = total == 0 ? 0.0 : (present * 100.0 / total);
        if (donutView != null) {
            donutView.setProgress((float) overall);
        }
		recyclerView.setAdapter(new SubjectAdapter(rows));
	}

	static class SubjectRow {
        final String subject;
        final int totalClasses;
        final int attendedClasses;
        final int percent;
        SubjectRow(String subject, int totalClasses, int attendedClasses, int percent) {
            this.subject = subject;
            this.totalClasses = totalClasses;
            this.attendedClasses = attendedClasses;
            this.percent = percent;
        }
	}

	static class SubjectAdapter extends RecyclerView.Adapter<SubjectViewHolder> {
		private final List<SubjectRow> data;
		SubjectAdapter(List<SubjectRow> data) { this.data = data; }
		@Override public SubjectViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
			android.view.View v = android.view.LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_subject_row, parent, false);
			return new SubjectViewHolder(v);
		}
		@Override public void onBindViewHolder(@NonNull SubjectViewHolder h, int i) { h.bind(data.get(i)); }
		@Override public int getItemCount() { return data.size(); }
	}

	static class SubjectViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSubject, tvTotal, tvAttended, tvPercent;
		SubjectViewHolder(@NonNull View itemView) {
			super(itemView);
            tvSubject = itemView.findViewById(R.id.tv_subject);
            tvTotal = itemView.findViewById(R.id.tv_total);
            tvAttended = itemView.findViewById(R.id.tv_attended);
            tvPercent = itemView.findViewById(R.id.tv_percent);
		}
		void bind(SubjectRow row) {
			tvSubject.setText(row.subject);
            tvTotal.setText(String.valueOf(row.totalClasses));
            tvAttended.setText(String.valueOf(row.attendedClasses));
            tvPercent.setText(String.format(Locale.getDefault(), "%d%%", row.percent));
		}
	}
}
