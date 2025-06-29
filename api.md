# Scrum Game API

<details>
  <summary><strong>Table of Contents</strong></summary>

  * [Query](#query)
  * [Mutation](#mutation)
  * [Objects](#objects)
    * [Assignment](#assignment)
    * [AssignmentCompletedFeedback](#assignmentcompletedfeedback)
    * [AssignmentMutation](#assignmentmutation)
    * [CodeAssignmentGradingMetadata](#codeassignmentgradingmetadata)
    * [CodeAssignmentMetadata](#codeassignmentmetadata)
    * [Exercise](#exercise)
    * [ExerciseGrading](#exercisegrading)
    * [ExternalAssignment](#externalassignment)
    * [ExternalCourse](#externalcourse)
    * [Grading](#grading)
    * [ManualMappingInstance](#manualmappinginstance)
    * [PaginationInfo](#paginationinfo)
    * [StudentMapping](#studentmapping)
    * [Subexercise](#subexercise)
    * [SubexerciseGrading](#subexercisegrading)
    * [UnfinishedGrading](#unfinishedgrading)
  * [Inputs](#inputs)
    * [CreateAssignmentInput](#createassignmentinput)
    * [CreateExerciseInput](#createexerciseinput)
    * [CreateSubexerciseInput](#createsubexerciseinput)
    * [DateTimeFilter](#datetimefilter)
    * [ExerciseCompletedInput](#exercisecompletedinput)
    * [IntFilter](#intfilter)
    * [LogAssignmentCompletedInput](#logassignmentcompletedinput)
    * [Pagination](#pagination)
    * [StringFilter](#stringfilter)
    * [StudentMappingInput](#studentmappinginput)
    * [SubexerciseCompletedInput](#subexercisecompletedinput)
    * [UpdateAssignmentInput](#updateassignmentinput)
    * [UpdateExerciseInput](#updateexerciseinput)
    * [UpdateSubexerciseInput](#updatesubexerciseinput)
  * [Enums](#enums)
    * [AssignmentType](#assignmenttype)
    * [SortDirection](#sortdirection)
  * [Scalars](#scalars)
    * [Boolean](#boolean)
    * [Date](#date)
    * [DateTime](#datetime)
    * [Float](#float)
    * [Int](#int)
    * [LocalTime](#localtime)
    * [String](#string)
    * [Time](#time)
    * [UUID](#uuid)
    * [Url](#url)

</details>

## Query
<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="query.findassignmentsbyassessmentids">findAssignmentsByAssessmentIds</strong></td>
<td valign="top">[<a href="#assignment">Assignment</a>]!</td>
<td>

Get assignment by assessment ID.
If any of the assessment IDs are not found, the corresponding assignment will be null.
🔒 The user must be enrolled in the course the assignments belong to to access them. Otherwise null is returned for
an assignment if the user has no access to it.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">assessmentIds</td>
<td valign="top">[<a href="#uuid">UUID</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="query.getgradingsforassignment">getGradingsForAssignment</strong></td>
<td valign="top">[<a href="#grading">Grading</a>!]!</td>
<td>

Get all gradings for one assignment
🔒 The user must be an admin in the course the assignment belongs to to access them. Otherwise null is returned for
an assignment if the user has no access to it.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">assessmentId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="query.getexternalassignments">getExternalAssignments</strong></td>
<td valign="top">[<a href="#externalassignment">ExternalAssignment</a>!]!</td>
<td>

Gets all the available external exercises.
CourseId is the id of the course the user is currently working in.
🔒 The user must be an admin in the course. Otherwise null is returned.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="query.getexternalcodeassignments">getExternalCodeAssignments</strong></td>
<td valign="top">[<a href="#string">String</a>!]!</td>
<td>

Gets all the available external code exercises.
CourseId is the id of the course the user is currently working in.
🔒 The user must be an admin in the course. Otherwise null is returned.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="query.getexternalcourse">getExternalCourse</strong></td>
<td valign="top"><a href="#externalcourse">ExternalCourse</a></td>
<td>

Get the corresponding external course for the given courseId.
CourseId is the id of the course the user is currently working in.
🔒 The user must be an admin in the course. Otherwise null is returned.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="query.getmanualmappinginstances">getManualMappingInstances</strong></td>
<td valign="top">[<a href="#manualmappinginstance">ManualMappingInstance</a>]!</td>
<td>

Gets all manual student mappings, i.e. all students where the backend could not map to a meitrex user.
🔒 The user must be an admin in the course. Otherwise null is returned.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
</tbody>
</table>

## Mutation
Mutations for the assignment service. Provides mutations for creating, updating, and deleting assignments.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="mutation._internal_noauth_createassignment">_internal_noauth_createAssignment</strong></td>
<td valign="top"><a href="#assignment">Assignment</a>!</td>
<td>

Creates a new assignment. Mutation is only accessible internally within the system by other
services and the gateway.
⚠️ This mutation is only accessible internally in the system and allows the caller to create assignments without
any permissions check and should not be called without any validation of the caller's permissions. ⚠️

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">assessmentId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#createassignmentinput">CreateAssignmentInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="mutation.updateassignment">updateAssignment</strong></td>
<td valign="top"><a href="#assignment">Assignment</a>!</td>
<td>

Update top-level fields of an assignment.
🔒 The user must be an admin in the course the assignment belongs to.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">assessmentId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#updateassignmentinput">UpdateAssignmentInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="mutation.mutateassignment">mutateAssignment</strong></td>
<td valign="top"><a href="#assignmentmutation">AssignmentMutation</a>!</td>
<td>

Modify an assignment.
🔒 The user must be an admin in the course the assignment is in to perform this action.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">assessmentId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="mutation.logassignmentcompleted">logAssignmentCompleted</strong></td>
<td valign="top"><a href="#assignmentcompletedfeedback">AssignmentCompletedFeedback</a>!</td>
<td>

Logs that a user's assignment score has been imported, i.e. the user has completed the assignment.
🔒 The user must be a tutor in the course the assignment is in to perform this action.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#logassignmentcompletedinput">LogAssignmentCompletedInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="mutation.savestudentmappings">saveStudentMappings</strong></td>
<td valign="top">[<a href="#string">String</a>]!</td>
<td>

Saves mappings of meitrex users to external students.
Used to deal with ManualMappingInstances.
Returns list of all deleted ManualMappingInstance ids.
Returns null if connection to UserService failed.
🔒 The user must be an admin in the course to perform this action.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">studentMappingInputs</td>
<td valign="top">[<a href="#studentmappinginput">StudentMappingInput</a>!]!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="mutation.syncassignmentsforcourse">syncAssignmentsForCourse</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td>

Fetches assignment info from external code assessment provider for the given course

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">courseId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
</tbody>
</table>

## Objects

### Assignment

An assignment is an external source of tasks, which can be imported. This includes exercise sheets and physical tests.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="assignment.assessmentid">assessmentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Identifier of the assignment, same as the identifier of the assessment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.courseid">courseId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Id of the course this assignment belongs to.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.exercises">exercises</strong></td>
<td valign="top">[<a href="#exercise">Exercise</a>!]</td>
<td>

List of exercises making up the assignment.
Optional for CODE_ASSIGNMENT since GH Classroom does not provide exercises.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.date">date</strong></td>
<td valign="top"><a href="#datetime">DateTime</a></td>
<td>

The date at which the assignment had to be handed in (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.totalcredits">totalCredits</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td>

Number of total credits in the assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.assignmenttype">assignmentType</strong></td>
<td valign="top"><a href="#assignmenttype">AssignmentType</a>!</td>
<td>

Type of the assignment, e.g. exercise sheet or physical test.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.description">description</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Description of the assignment (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.requiredpercentage">requiredPercentage</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td>

The required percentage to pass the assignment. A value between 0 and 1. Defaults to 0.5. (optional)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.externalid">externalId</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The id of the exercise sheet in an external system like TMS. (optional)
This is needed for mapping grading data to assignments.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignment.codeassignmentmetadata">codeAssignmentMetadata</strong></td>
<td valign="top"><a href="#codeassignmentmetadata">CodeAssignmentMetadata</a></td>
<td>

CodeAssignmentMetadata contains metadata for the external code assignment.

</td>
</tr>
</tbody>
</table>

### AssignmentCompletedFeedback

Feedback data when "logAssignmentCompleted" is called.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="assignmentcompletedfeedback.success">success</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td>

Whether the assignment was passed or not.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentcompletedfeedback.correctness">correctness</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The percentage of achieved credits compared to total credits.

</td>
</tr>
</tbody>
</table>

### AssignmentMutation

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.assessmentid">assessmentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the assignment that is being modified.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.createexercise">createExercise</strong></td>
<td valign="top"><a href="#exercise">Exercise</a>!</td>
<td>

Creates a new exercise. Throws an error if the assignment does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#createexerciseinput">CreateExerciseInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.updateexercise">updateExercise</strong></td>
<td valign="top"><a href="#exercise">Exercise</a>!</td>
<td>

Updates an exercise. Throws an error if the exercise does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#updateexerciseinput">UpdateExerciseInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.deleteexercise">deleteExercise</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Deletes the exercise with the specified ID. Throws an error if the exercise does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">itemId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.createsubexercise">createSubexercise</strong></td>
<td valign="top"><a href="#subexercise">Subexercise</a>!</td>
<td>

Creates a new subexercise. Throws an error if the assignment does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#createsubexerciseinput">CreateSubexerciseInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.updatesubexercise">updateSubexercise</strong></td>
<td valign="top"><a href="#subexercise">Subexercise</a>!</td>
<td>

Updates a subexercise. Throws an error if the subexercise does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">input</td>
<td valign="top"><a href="#updatesubexerciseinput">UpdateSubexerciseInput</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="assignmentmutation.deletesubexercise">deleteSubexercise</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Deletes the subexercise with the specified ID. Throws an error if the subexercise does not exist.

</td>
</tr>
<tr>
<td colspan="2" align="right" valign="top">itemId</td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td></td>
</tr>
</tbody>
</table>

### CodeAssignmentGradingMetadata

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentgradingmetadata.repolink">repoLink</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The repository link of for the external code assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentgradingmetadata.status">status</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The pipeline status of the corresponding repository.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentgradingmetadata.feedbacktablehtml">feedbackTableHtml</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The Github worfklow run log table HTML of the corresponding repository.

</td>
</tr>
</tbody>
</table>

### CodeAssignmentMetadata

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentmetadata.assignmentlink">assignmentLink</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Link to the GitHub Classroom or equivalent (optional, CODE_ASSIGNMENT only).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentmetadata.invitationlink">invitationLink</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Invitation link for students to join the assignment (optional, CODE_ASSIGNMENT only).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="codeassignmentmetadata.readmehtml">readmeHtml</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

README content in HTML format for the assignment (optional, CODE_ASSIGNMENT only).

</td>
</tr>
</tbody>
</table>

### Exercise

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="exercise.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Unique identifier of the exercise and the id of the corresponding item

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercise.totalexercisecredits">totalExerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this exercise including all sub-exercises.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercise.subexercises">subexercises</strong></td>
<td valign="top">[<a href="#subexercise">Subexercise</a>!]!</td>
<td>

Sub-exercises making up the exercise, i.e. parts a),b),c),...

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercise.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2 (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercise.tutorfeedback">tutorFeedback</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Feedback given by a tutor on the exercise (optional).

</td>
</tr>
</tbody>
</table>

### ExerciseGrading

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="exercisegrading.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the exercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercisegrading.studentid">studentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the student the exercise-grading belongs to.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercisegrading.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The absolute number of achieved credits on the exercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercisegrading.subexercisegradings">subexerciseGradings</strong></td>
<td valign="top">[<a href="#subexercisegrading">SubexerciseGrading</a>]!</td>
<td>

List of subexercise-gradings for each subexercise in the exercise. Can be empty, if there are no subexercises within the exercise.

</td>
</tr>
</tbody>
</table>

### ExternalAssignment

An external Assignment such as the ones from TMS. These are needed for mapping Meitrex Assignments to external ones for importing gradings.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="externalassignment.externalid">externalId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td></td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="externalassignment.sheetno">sheetNo</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td></td>
</tr>
</tbody>
</table>

### ExternalCourse

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="externalcourse.coursetitle">courseTitle</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

The name of the course.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="externalcourse.url">url</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

The url to the course.

</td>
</tr>
</tbody>
</table>

### Grading

A grading contains a user's achieved credits on an assignment and its exercises and subexercises.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="grading.assessmentid">assessmentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="grading.studentid">studentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the student the grading belongs to.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="grading.date">date</strong></td>
<td valign="top"><a href="#datetime">DateTime</a></td>
<td>

The date and time of when the tutor corrected the assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="grading.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td>

The absolute number of achieved credits on the assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="grading.codeassignmentgradingmetadata">codeAssignmentGradingMetadata</strong></td>
<td valign="top"><a href="#codeassignmentgradingmetadata">CodeAssignmentGradingMetadata</a></td>
<td>

CodeAssignmentGradingMetadata contains metadata for the external code assignment grading.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="grading.exercisegradings">exerciseGradings</strong></td>
<td valign="top">[<a href="#exercisegrading">ExerciseGrading</a>!]</td>
<td>

List of exercise-gradings for each exercise in the assignment. Can be empty, if there are no exercises within the assignment.

</td>
</tr>
</tbody>
</table>

### ManualMappingInstance

An object to represent a student where the backend could not automatically map the external student to a meitrex user.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="manualmappinginstance.externalstudentid">externalStudentId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Student Id in external system like TMS

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="manualmappinginstance.externalstudentinfo">externalStudentInfo</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

JSON Object containing all available information on the external student.

</td>
</tr>
</tbody>
</table>

### PaginationInfo

Return type for information about paginated results.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="paginationinfo.page">page</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The current page number.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="paginationinfo.size">size</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The number of elements per page.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="paginationinfo.totalelements">totalElements</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The total number of elements across all pages.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="paginationinfo.totalpages">totalPages</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The total number of pages.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="paginationinfo.hasnext">hasNext</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td>

Whether there is a next page.

</td>
</tr>
</tbody>
</table>

### StudentMapping

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="studentmapping.meitrexstudentid">meitrexStudentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Student Id in Meitrex

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="studentmapping.externalstudentid">externalStudentId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Student Id in external system like TMS

</td>
</tr>
</tbody>
</table>

### Subexercise

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="subexercise.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Unique identifier of the exercise and the id of the corresponding item

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercise.totalsubexercisecredits">totalSubexerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this sub-exercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercise.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2b (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercise.tutorfeedback">tutorFeedback</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Feedback given by a tutor on the exercise (optional).

</td>
</tr>
</tbody>
</table>

### SubexerciseGrading

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="subexercisegrading.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the subexercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercisegrading.studentid">studentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the student the subexercise-grading belongs to.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercisegrading.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The absolute number of achieved credits on the subexercise.

</td>
</tr>
</tbody>
</table>

### UnfinishedGrading

An Unfinished Grading is created, when importing and parsing gradings from external systems like TMS goes wrong
because the student id has to be mapped manually.
After an admin mapped ids manually, these unfinished gradings will be tried again.

<table>
<thead>
<tr>
<th align="left">Field</th>
<th align="right">Argument</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="unfinishedgrading.externalstudentid">externalStudentId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Student Id in external system like TMS

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="unfinishedgrading.assignmentid">assignmentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Assignment/HandIn id in MEITREX

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="unfinishedgrading.gradingjson">gradingJson</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

JSON representation of the grading

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="unfinishedgrading.numberoftries">numberOfTries</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The number of times importing and parsing was tried. Might be useful for detecting and manually deleting broken gradings.

</td>
</tr>
</tbody>
</table>

## Inputs

### CreateAssignmentInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.totalcredits">totalCredits</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td>

Number of total credits in the assignment. Optional for CODE_ASSIGNMENT.
Can be set later when grades are available.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.exercises">exercises</strong></td>
<td valign="top">[<a href="#createexerciseinput">CreateExerciseInput</a>!]</td>
<td>

List of exercises in this Assignment
Optional for CODE_ASSIGNMENT since GH Classroom does not provide exercises.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.assignmenttype">assignmentType</strong></td>
<td valign="top"><a href="#assignmenttype">AssignmentType</a>!</td>
<td>

Type of the assignment, e.g. exercise sheet or physical test.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.date">date</strong></td>
<td valign="top"><a href="#datetime">DateTime</a></td>
<td>

The date at which the assignment had to be handed in (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.description">description</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

Description of the assignment (optional).

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.requiredpercentage">requiredPercentage</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td>

The required percentage to pass the assignment. A value between 0 and 1. Defaults to 0.5. (optional)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createassignmentinput.externalid">externalId</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The id of the exercise sheet in an external system like TMS. (optional)
This is needed for mapping grading data to assignments.

</td>
</tr>
</tbody>
</table>

### CreateExerciseInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="createexerciseinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

the id of the item the exercise belongs to

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createexerciseinput.totalexercisecredits">totalExerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this exercise including all sub-exercises. (Positive or zero)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createexerciseinput.subexercises">subexercises</strong></td>
<td valign="top">[<a href="#createsubexerciseinput">CreateSubexerciseInput</a>!]!</td>
<td>

Sub-exercises making up the exercise, i.e. parts a),b),c),...

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createexerciseinput.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2 (optional).

</td>
</tr>
</tbody>
</table>

### CreateSubexerciseInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="createsubexerciseinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

the id of the item the subexercise belongs to

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createsubexerciseinput.parentexerciseid">parentExerciseId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

the id of the exercise this subexercise belongs to

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createsubexerciseinput.totalsubexercisecredits">totalSubexerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this sub-exercise. (Positive or zero)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="createsubexerciseinput.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2b (optional).

</td>
</tr>
</tbody>
</table>

### DateTimeFilter

Filter for date values.
If multiple filters are specified, they are combined with AND.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="datetimefilter.after">after</strong></td>
<td valign="top"><a href="#datetime">DateTime</a></td>
<td>

If specified, filters for dates after the specified value.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="datetimefilter.before">before</strong></td>
<td valign="top"><a href="#datetime">DateTime</a></td>
<td>

If specified, filters for dates before the specified value.

</td>
</tr>
</tbody>
</table>

### ExerciseCompletedInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="exercisecompletedinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the exercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercisecompletedinput.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The absolute number of achieved credits.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="exercisecompletedinput.completedsubexercises">completedSubexercises</strong></td>
<td valign="top">[<a href="#subexercisecompletedinput">SubexerciseCompletedInput</a>]!</td>
<td>

List of subexercises that were completed in the exercise. Can be empty, if there are no subexercises within the exercise.

</td>
</tr>
</tbody>
</table>

### IntFilter

Filter for integer values.
If multiple filters are specified, they are combined with AND.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="intfilter.equals">equals</strong></td>
<td valign="top"><a href="#int">Int</a></td>
<td>

An integer value to match exactly.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="intfilter.greaterthan">greaterThan</strong></td>
<td valign="top"><a href="#int">Int</a></td>
<td>

If specified, filters for values greater than to the specified value.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="intfilter.lessthan">lessThan</strong></td>
<td valign="top"><a href="#int">Int</a></td>
<td>

If specified, filters for values less than to the specified value.

</td>
</tr>
</tbody>
</table>

### LogAssignmentCompletedInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="logassignmentcompletedinput.assessmentid">assessmentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the assignment.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="logassignmentcompletedinput.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The absolute number of achieved credits.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="logassignmentcompletedinput.completedexercises">completedExercises</strong></td>
<td valign="top">[<a href="#exercisecompletedinput">ExerciseCompletedInput</a>]!</td>
<td>

List of exercises that were completed in the assignment. Can be empty, if there are no exercises within the assignment.

</td>
</tr>
</tbody>
</table>

### Pagination

Specifies the page size and page number for paginated results.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="pagination.page">page</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The page number, starting at 0.
If not specified, the default value is 0.
For values greater than 0, the page size must be specified.
If this value is larger than the number of pages, an empty page is returned.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="pagination.size">size</strong></td>
<td valign="top"><a href="#int">Int</a>!</td>
<td>

The number of elements per page.

</td>
</tr>
</tbody>
</table>

### StringFilter

Filter for string values.
If multiple filters are specified, they are combined with AND.

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="stringfilter.equals">equals</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

A string value to match exactly.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="stringfilter.contains">contains</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

A string value that must be contained in the field that is being filtered.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="stringfilter.ignorecase">ignoreCase</strong></td>
<td valign="top"><a href="#boolean">Boolean</a>!</td>
<td>

If true, the filter is case-insensitive.

</td>
</tr>
</tbody>
</table>

### StudentMappingInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="studentmappinginput.meitrexstudentid">meitrexStudentId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Student Id in Meitrex

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="studentmappinginput.externalstudentid">externalStudentId</strong></td>
<td valign="top"><a href="#string">String</a>!</td>
<td>

Student Id in external system like TMS

</td>
</tr>
</tbody>
</table>

### SubexerciseCompletedInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="subexercisecompletedinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

ID of the subexercise.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="subexercisecompletedinput.achievedcredits">achievedCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The absolute number of achieved credits.

</td>
</tr>
</tbody>
</table>

### UpdateAssignmentInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="updateassignmentinput.requiredpercentage">requiredPercentage</strong></td>
<td valign="top"><a href="#float">Float</a></td>
<td></td>
</tr>
</tbody>
</table>

### UpdateExerciseInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="updateexerciseinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Id of the exercise to update.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="updateexerciseinput.totalexercisecredits">totalExerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this exercise including all sub-exercises. (Positive or zero)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="updateexerciseinput.subexercises">subexercises</strong></td>
<td valign="top">[<a href="#createsubexerciseinput">CreateSubexerciseInput</a>!]!</td>
<td>

Sub-exercises making up the exercise, i.e. parts a),b),c),...

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="updateexerciseinput.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2 (optional).

</td>
</tr>
</tbody>
</table>

### UpdateSubexerciseInput

<table>
<thead>
<tr>
<th colspan="2" align="left">Field</th>
<th align="left">Type</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td colspan="2" valign="top"><strong id="updatesubexerciseinput.itemid">itemId</strong></td>
<td valign="top"><a href="#uuid">UUID</a>!</td>
<td>

Id of the subexercise to update.

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="updatesubexerciseinput.totalsubexercisecredits">totalSubexerciseCredits</strong></td>
<td valign="top"><a href="#float">Float</a>!</td>
<td>

The amount of credits that can be earned on this sub-exercise. (Positive or zero)

</td>
</tr>
<tr>
<td colspan="2" valign="top"><strong id="updatesubexerciseinput.number">number</strong></td>
<td valign="top"><a href="#string">String</a></td>
<td>

The number of the exercise on the exercise sheet, may be something such as 2b (optional).

</td>
</tr>
</tbody>
</table>

## Enums

### AssignmentType

The type of assignment.

<table>
<thead>
<tr>
<th align="left">Value</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td valign="top"><strong>EXERCISE_SHEET</strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong>PHYSICAL_TEST</strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong>CODE_ASSIGNMENT</strong></td>
<td></td>
</tr>
</tbody>
</table>

### SortDirection

Specifies the sort direction, either ascending or descending.

<table>
<thead>
<tr>
<th align="left">Value</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td valign="top"><strong>ASC</strong></td>
<td></td>
</tr>
<tr>
<td valign="top"><strong>DESC</strong></td>
<td></td>
</tr>
</tbody>
</table>

## Scalars

### Boolean

The `Boolean` scalar type represents `true` or `false`.

### Date

### DateTime

### Float

The `Float` scalar type represents signed double-precision fractional values as specified by [IEEE 754](https://en.wikipedia.org/wiki/IEEE_floating_point).

### Int

The `Int` scalar type represents non-fractional signed whole numeric values. Int can represent values between -(2^31) and 2^31 - 1.

### LocalTime

### String

The `String` scalar type represents textual data, represented as UTF-8 character sequences. The String type is most often used by GraphQL to represent free-form human-readable text.

### Time

### UUID

### Url

