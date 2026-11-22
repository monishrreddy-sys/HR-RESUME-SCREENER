# HR Resume Screener – AI-Powered Candidate Shortlisting System

## Project Overview

The **HR Resume Screener** is a Java-based application that automates the initial resume screening process for Junior Java Developer roles.

It compares candidate resumes against a given job description, evaluates technical skills, assigns suitability scores, ranks candidates, and generates a CSV report containing the screening results.

The application integrates Google's Gemini AI API for intelligent resume evaluation and includes an offline keyword-based scoring mechanism as a fallback when the API is unavailable.

## Features

- **Automated Resume Loading:** Automatically detects and reads `.txt` files from the `resumes` folder.
- **AI-Powered Evaluation:** Uses the Gemini API to evaluate candidate skills and job relevance.
- **Offline Scoring:** Automatically switches to keyword-based scoring when Gemini is unavailable.
- **Retry Mechanism:** Allows up to 3 API attempts, with a 5-second timeout per attempt.
- **Resume Validation:** Detects empty files, extremely short content, and unreadable files.
- **Candidate Scoring:** Assigns scores from 0 to 10.
- **Automated Ranking:** Sorts candidates based on their scores.
- **Shortlisting:** Candidates scoring 7 or above are marked as SHORTLISTED.
- **CSV Report Generation:** Exports all evaluated candidates, scores, justifications, and scoring methods into `shortlist_report.csv`.

## Technologies Used

- Java (JDK 11 or later)
- Object-Oriented Programming (OOP)
- Google Gemini API
- Java HTTP Client
- Java NIO (File Handling)
- Regular Expressions
- CSV File Generation
- Git and GitHub

## OOP Concepts Implemented

| Concept | Implementation |
|---|---|
| Encapsulation | Resume and CandidateScore classes |
| Abstraction | ScoringStrategy interface |
| Polymorphism | GeminiAIScorer and KeywordFallbackScorer |
| Comparable | Candidate ranking using descending score order |
| Strategy Design Pattern | Interchangeable AI and offline scoring strategies |

## Project Structure

```text
ResumeScreener/
│
├── ResumeScreenerApp.java
├── job_description.txt
│
├── resumes/
│   ├── candidate1.txt
│   ├── candidate2.txt
│   └── candidate3.txt
│
└── shortlist_report.csv
```

## Setup and Installation

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
```

### 2. Navigate to the project directory

```bash
cd YOUR_REPOSITORY
```

### 3. Configure the job description

Add the required job description to:

```text
job_description.txt
```

### 4. Add candidate resumes

Place all candidate `.txt` resume files inside:

```text
resumes/
```

New resume files are automatically detected when the application runs again.

### 5. Configure Gemini API Key

Set your Gemini API key as an environment variable.

**Windows PowerShell:**

```powershell
$env:GEMINI_API_KEY="YOUR_API_KEY"
```

**Linux/macOS:**

```bash
export GEMINI_API_KEY="YOUR_API_KEY"
```

Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).

## Running the Application

Compile the Java program:

```bash
javac ResumeScreenerApp.java
```

Run the application:

```bash
java ResumeScreenerApp
```

If a Gemini API key is configured, the application attempts AI-based evaluation. Otherwise, it directly uses the offline keyword-based scorer.

## Scoring Mechanism

The application uses two scoring strategies:

**1. Gemini AI Scoring**

- Compares the job description with the candidate's resume.
- Evaluates technical skills and job relevance.
- Generates a score and a concise justification.

**2. Offline Keyword Scoring**

- Checks for required skills such as Java, OOP, Collections, Exception Handling, SQL, MySQL, Spring Boot, REST API, Git, and JUnit.
- Calculates a score based on matched required skills.
- Caps the score at 3 if Java is missing.

**Resume Validation:**

- Empty resumes receive 0/10.
- Files containing fewer than three words receive 0/10.
- Unreadable files are retained in the report with a 0/10 score.

## API Reliability and Fallback

To handle temporary API failures, the application implements:

- A maximum of 3 attempts per resume.
- A 5-second timeout per request.
- A 500-millisecond delay between retries.
- Automatic switching to offline scoring after unsuccessful attempts.

This allows the screening process to continue even when Gemini is unavailable.

## Output

After execution, the application generates:

```text
shortlist_report.csv
```

The report contains:

- Candidate rank
- Resume filename
- Suitability score
- Shortlisting status
- Scoring method
- Evaluation justification

**Shortlisting Criteria:**

| Score | Status |
|---|---|
| 7–10 | SHORTLISTED |
| 0–6 | REJECTED |

## Future Enhancements

- PDF and DOCX resume parsing.
- OCR support for scanned resumes.
- Advanced semantic resume matching.
- More configurable job roles and skill requirements.
- Improved candidate evaluation and reporting.

## Author

Developed as an academic project for **Object Oriented Programming with Java**.

---

**Note:** This application is designed for preliminary resume screening. Final recruitment decisions should involve human review.
