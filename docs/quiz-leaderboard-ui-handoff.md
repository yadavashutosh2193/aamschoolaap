# Quiz Leaderboard UI Handoff

## Purpose

Build a leaderboard page/component for a specific quiz.

The page should:
- show the current quiz title
- show ranked users for that quiz only
- highlight the current logged-in user if present
- show score, accuracy, and time taken
- refresh after quiz submission

This leaderboard is quiz-specific, not a global leaderboard.

## Backend Endpoints

### 1. Submit Quiz Attempt

Endpoint:
`POST /quizzes/{quizId}/submit`

Example:
`POST /quizzes/12/submit`

Use this after the user completes the quiz.

Request JSON:

```json
{
  "userId": 45,
  "timeTakenSeconds": 512,
  "answers": [
    {
      "quizQuestionId": 101,
      "selectedChoiceId": 1001
    },
    {
      "quizQuestionId": 102,
      "selectedChoiceId": 1008
    }
  ]
}
```

Request field notes:
- `userId`: required only if the UI is not sending authenticated JWT. If JWT is used, backend resolves the user from the token.
- `timeTakenSeconds`: total quiz duration in seconds.
- `answers[].quizQuestionId`: use the `id` of each quiz question from `GET /quizzes/{quizId}`.
- `answers[].selectedChoiceId`: use the selected option id for that quiz question.

Success response:

```json
{
  "quizId": 12,
  "attemptId": 7,
  "userId": 45,
  "username": "rahul",
  "score": 18.0,
  "totalMarks": 20,
  "correctAnswers": 18,
  "wrongAnswers": 2,
  "attemptedQuestions": 20,
  "accuracy": 90.0,
  "timeTakenSeconds": 512,
  "submittedAt": "2026-03-03T18:12:44.123"
}
```

### 2. Fetch Quiz Leaderboard

Endpoint:
`GET /quizzes/{quizId}/leaderboard`

Example:
`GET /quizzes/12/leaderboard`

Use this to render the leaderboard page for one quiz.

Success response:

```json
{
  "quizId": 12,
  "quizTitle": "Polity - Fundamental Rights - Set 1",
  "totalParticipants": 3,
  "generatedAt": "2026-03-03T18:20:00.000",
  "entries": [
    {
      "rank": 1,
      "user": {
        "userId": 45,
        "username": "rahul"
      },
      "score": 18.0,
      "totalMarks": 20,
      "correctAnswers": 18,
      "wrongAnswers": 2,
      "attemptedQuestions": 20,
      "accuracy": 90.0,
      "timeTakenSeconds": 512,
      "submittedAt": "2026-03-03T18:12:44.123",
      "isCurrentUser": false
    },
    {
      "rank": 2,
      "user": {
        "userId": 67,
        "username": "priya"
      },
      "score": 18.0,
      "totalMarks": 20,
      "correctAnswers": 18,
      "wrongAnswers": 2,
      "attemptedQuestions": 20,
      "accuracy": 90.0,
      "timeTakenSeconds": 530,
      "submittedAt": "2026-03-03T18:15:20.000",
      "isCurrentUser": true
    }
  ]
}
```

### 3. Fetch Quiz Details

Endpoint:
`GET /quizzes/{quizId}`

Example:
`GET /quizzes/12`

Use this on the quiz-taking page to get the list of quiz questions and options.

Important mapping:
- `questions[].id` -> send as `quizQuestionId`
- `questions[].question.choices[].id` -> send as `selectedChoiceId`

Example fragment:

```json
{
  "id": 12,
  "title": "Polity - Fundamental Rights - Set 1",
  "questions": [
    {
      "id": 101,
      "orderIndex": 1,
      "question": {
        "id": 5001,
        "questionText": "Which article guarantees equality before law?",
        "choices": [
          {
            "id": 1001,
            "text": "Article 14"
          },
          {
            "id": 1002,
            "text": "Article 19"
          }
        ]
      }
    }
  ]
}
```

## Recommended UI Flow

### Quiz Taking Screen

1. Call `GET /quizzes/{quizId}`.
2. Render all quiz questions in order.
3. Store selected choice per `quizQuestionId`.
4. Start a timer when the quiz begins.
5. On submit:
   send `POST /quizzes/{quizId}/submit`.
6. On successful submission:
   navigate to the leaderboard page for the same quiz.

### Leaderboard Screen

1. Call `GET /quizzes/{quizId}/leaderboard`.
2. Render the quiz title from `quizTitle`.
3. Render participant count from `totalParticipants`.
4. Render `entries[]` in a ranked table/list.
5. Highlight row where `isCurrentUser = true`.
6. Optionally support manual refresh.

## Required UI Data Model

Recommended frontend types:

```ts
type QuizSubmissionAnswer = {
  quizQuestionId: number;
  selectedChoiceId: number;
};

type QuizSubmitRequest = {
  userId?: number;
  timeTakenSeconds: number;
  answers: QuizSubmissionAnswer[];
};

type LeaderboardUser = {
  userId: number;
  username: string;
};

type LeaderboardEntry = {
  rank: number;
  user: LeaderboardUser;
  score: number;
  totalMarks: number;
  correctAnswers: number;
  wrongAnswers: number;
  attemptedQuestions: number;
  accuracy: number;
  timeTakenSeconds: number | null;
  submittedAt: string;
  isCurrentUser: boolean;
};

type QuizLeaderboardResponse = {
  quizId: number;
  quizTitle: string;
  totalParticipants: number;
  generatedAt: string;
  entries: LeaderboardEntry[];
};
```

## Leaderboard Table Columns

Minimum recommended columns:
- Rank
- User
- Score
- Accuracy
- Correct / Wrong
- Time Taken

Optional extra columns:
- Attempted
- Submitted At

Recommended display examples:
- Rank: `#1`
- Score: `18 / 20`
- Accuracy: `90%`
- Correct / Wrong: `18 / 2`
- Time Taken: `08:32`

## Sorting Logic

The backend already returns entries in leaderboard order.

Do not re-sort on the UI unless there is a product requirement.

Backend ranking priority:
1. Higher `score`
2. Higher `correctAnswers`
3. Lower `timeTakenSeconds`
4. Earlier `submittedAt`

## UI States

### Loading State

Show:
- page header skeleton
- leaderboard table skeleton rows

### Empty State

If `entries` is empty:
- show quiz title
- show message: `No attempts yet for this quiz.`

### Error State

If leaderboard API fails:
- show message: `Unable to load leaderboard. Please try again.`
- show retry action

If submit API fails:
- show message near submit action
- keep selected answers in UI state so the user does not lose progress

## Current User Highlighting

If `isCurrentUser` is `true`:
- visually highlight the row
- optionally show badge: `You`

Do not try to infer current user manually if `isCurrentUser` is already returned by the API.

## Privacy Rules

The leaderboard should display:
- username only

Do not display:
- email
- phone

## API Integration Notes

- If the UI uses JWT auth, send the bearer token as usual.
- If authenticated, backend can resolve the user from the token for submit and current-user highlight.
- If not authenticated, send `userId` in the submit payload.

## Suggested Routes

Recommended frontend routes:
- `/quiz/:quizId` for quiz taking page
- `/quiz/:quizId/leaderboard` for leaderboard page

## Suggested Component Breakdown

- `QuizPage`
- `QuizQuestionCard`
- `QuizTimer`
- `SubmitQuizButton`
- `QuizLeaderboardPage`
- `LeaderboardHeader`
- `LeaderboardTable`
- `LeaderboardRow`

## Acceptance Criteria

The UI is complete when:
- it can load a quiz by id
- it can submit quiz answers in the backend request shape
- it redirects to the quiz-specific leaderboard after submit
- it shows leaderboard rows in backend order
- it highlights the current user row
- it correctly handles loading, empty, and error states
