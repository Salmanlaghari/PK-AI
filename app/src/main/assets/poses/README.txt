PK AI Super Chat — Avatar Pose Sheets
=====================================

Drop your avatar sprite sheets in this folder using these exact names:

    poses_sheet_1.png   (stickers 1–20)
    poses_sheet_2.png   (stickers 21–40)
    poses_sheet_3.png   (stickers 41–60)
    poses_sheet_4.png   (stickers 61–80)
    poses_sheet_5.png   (stickers 81–100)
    ... up to poses_sheet_10.png (stickers 191–200)

Sheet format (important — slicing depends on it):
- Uniform 5-column x 4-row grid = 20 cells per sheet
- Cells numbered ROW-MAJOR: left→right, top→bottom
- Every cell the same size; character anchored bottom-center of each cell
- Transparent or dark background both work

The app slices these at runtime automatically. Until a sheet is present, a
built-in placeholder pose is shown for its sticker range — the app never breaks.
