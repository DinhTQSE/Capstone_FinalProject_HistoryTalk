# Tesseract OCR Language Data

This directory contains Tesseract trained data files for OCR processing.

## Required Files

- **vie.traineddata**: Vietnamese language data file

## How to Download

Download `vie.traineddata` from the official Tesseract GitHub repository:

https://github.com/tesseract-ocr/tessdata/raw/main/vie.traineddata

Place the file in this directory (`src/main/resources/tessdata/`).

## Alternative Languages

Additional language data can be downloaded from:
https://github.com/tesseract-ocr/tessdata

Common languages:
- `eng.traineddata` - English
- `vie.traineddata` - Vietnamese
- `chi_sim.traineddata` - Simplified Chinese
- `chi_tra.traineddata` - Traditional Chinese

## Note

The application is configured to use Vietnamese (`vie`) by default in `application.properties`.
