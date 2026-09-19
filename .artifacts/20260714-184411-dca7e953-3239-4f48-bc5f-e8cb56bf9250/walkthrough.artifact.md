# Walkthrough: Automated & Tailored Group Documents

This task implemented a system for generating, downloading, and uploading group-type-specific documents (Constitution and Membership Contract) which serve as mandatory prerequisites for full membership.

## 🚀 Key Accomplishments

### 1. Tailored Content Generation
- **DocumentGeneratorService**: Now generates detailed content for each of the 5 group types (Burial Society, Stokvel, ROSCA, Investment Club, Emergency Fund).
- **Constitution**: Includes specific Objectives, Loan Rules, and Special Provisions (e.g., waiting periods for Burial Societies, rotation logic for ROSCAs).
- **Membership Contract**: Includes tailored legal clauses and obligations relevant to the group's financial model.

### 2. Dynamic PDF Export
- **ExportRepositoryImpl**: Integrated with the generation service to produce high-quality PDFs.
- The **Group Constitution** and **Membership Contract** are now dynamically built from structured sections, ensuring professional formatting and relevant content.

### 3. Workflow & Prerequisites
- The **Membership Contract** is verified as a mandatory prerequisite in the domain layer (`UpdateMemberStatusUseCase` and `MemberActuarialService`).
- A member cannot transition from `PROBATION` to `ACTIVE` (Full Membership) without a verified contract.

### 4. UI Enhancements
- **DocumentUploadCard**: Added a "Download Template" button allowing members to get the tailored PDF directly from the upload area.
- **MemberDocumentsTab**: Wired up the templates for easy access.

---

## 🛠️ Technical Changes

### Domain Layer
- Updated [DocumentGeneratorService.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/service/DocumentGeneratorService.kt) to include structured content generation for all group types.

### Data Layer
- Updated [ExportRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/ExportRepositoryImpl.kt) to use the new generation logic for PDFs.
- Fixed [MemberRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/MemberRepositoryImpl.kt) to handle the specialized upload of membership contracts (index 100).

### Presentation Layer
- Enhanced [DocumentComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/DocumentComponents.kt) with download template support.
- Updated [MemberDocumentsTab.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/member/tabs/MemberDocumentsTab.kt) to expose these templates.

---

## 📖 Documentation
A new reference document was created: [AUTOMATED_DOCUMENTS.md](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/docs/AUTOMATED_DOCUMENTS.md).
