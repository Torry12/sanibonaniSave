# Implementation Plan: Automated & Tailored Group Documents

This plan outlines the implementation of automated, downloadable, and tailored documents for group constitutions and membership contracts.

## Goal
Create a system where members can download tailored legal documents based on their group type, which are then uploaded and verified as a prerequisite for full membership status.

## Proposed Changes

### Domain Component
- **DocumentGeneratorService**: Add logic to generate structured content for each group type.
- **MemberActuarialService / UpdateMemberStatusUseCase**: Ensure documentation check is strictly enforced.

### Data Component
- **ExportRepositoryImpl**: Update PDF generation to use `DocumentGeneratorService`.
- **MemberRepositoryImpl**: Fix upload logic for membership contracts.

### UI Component
- **DocumentComponents**: Add download template functionality to `DocumentUploadCard`.
- **MemberDocumentsTab**: Wire up download and upload actions.

---

## Verification Plan

### Manual Verification
1.  **PDF Content Check**: Download the Constitution and Membership Contract for different group types (e.g., Burial Society and ROSCA) and verify that the content (Objectives, Clauses) matches the group type.
2.  **Upload Workflow**: Upload a document for the "Membership Contract" and verify that it appears as "Pending Verification" and is correctly stored in Supabase under the `contract_url` column.
3.  **Status Transition**: Try to transition a member to `ACTIVE` without a verified contract; verify that the system blocks the transition with the correct error message.
