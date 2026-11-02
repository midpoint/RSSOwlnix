/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2003-2011 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License 1.0 which accompanies this     **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl - initial API and implementation (bpasero@rssowl.org)         **
 **                                                                          **
 **  **********************************************************************  */

/**
 * NSIS installer script for the Windows product produced by Tycho.
 *
 * Build the product first:
 *   mvn -pl '!tests,!org.rssowl.core.tests' -DskipTests package
 *
 * Then run makensis from the releng/product directory, for example:
 *   makensis /DPRODUCT_DIR=target/products/org.rssowl.ui.product/win32/win32/x86_64/RSSOwlnix \
 *            /DOUTPUT_FILE=target/products/RSSOwlnix-3.0.1-win32.win32.x86_64-Setup.exe \
 *            rssowl_installer.nsi
 *
 * PRODUCT_DIR, PRODUCT_ICON and OUTPUT_FILE can be overridden on the command
 * line. Relative paths are resolved against the current working directory.
 */

Unicode true

!define PRODUCT_NAME "RSSOwlnix"
!define PRODUCT_EXE "RSSOwlnix.exe"
!define PRODUCT_VERSION "3.0.1"
!define PRODUCT_PUBLISHER "RSSOwlnix"
!define PRODUCT_WEB_SITE "https://github.com/RSSOwlnix/RSSOwlnix"

!ifndef PRODUCT_DIR
  !define PRODUCT_DIR "target/products/org.rssowl.ui.product/win32/win32/x86_64/RSSOwlnix"
!endif

!ifndef PRODUCT_ICON
  !define PRODUCT_ICON "../../org.rssowl.ui/icons/product/rssowl.ico"
!endif

!ifndef OUTPUT_FILE
  !define OUTPUT_FILE "target/products/RSSOwlnix-${PRODUCT_VERSION}-win32.win32.x86_64-Setup.exe"
!endif

;#####   Installer Configuration   ######
Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$PROGRAMFILES64\${PRODUCT_NAME}"
InstallDirRegKey HKLM "Software\${PRODUCT_NAME}" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
BrandingText "${PRODUCT_NAME} ${PRODUCT_VERSION}"
VIProductVersion "${PRODUCT_VERSION}.0"
VIAddVersionKey "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey "CompanyName" "${PRODUCT_PUBLISHER}"
VIAddVersionKey "FileDescription" "${PRODUCT_NAME} Setup"
VIAddVersionKey "FileVersion" "${PRODUCT_VERSION}"
VIAddVersionKey "ProductVersion" "${PRODUCT_VERSION}"
VIAddVersionKey "LegalCopyright" "RSSOwl Development Team"

;#####   Include Modern UI   ######
!include "MUI2.nsh"

;#####   Variables   ######
Var STARTMENU_FOLDER
Var MUI_TEMP

;#####   Interface Settings   ######
!define MUI_ABORTWARNING
!define MUI_ICON "${PRODUCT_ICON}"
!define MUI_UNICON "${PRODUCT_ICON}"
!define MUI_FINISHPAGE_RUN "$INSTDIR\${PRODUCT_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Launch ${PRODUCT_NAME}"
!define MUI_FINISHPAGE_LINK "${PRODUCT_NAME} on GitHub"
!define MUI_FINISHPAGE_LINK_LOCATION "${PRODUCT_WEB_SITE}"

;### Remember language ###
!define MUI_LANGDLL_REGISTRY_ROOT "HKCU"
!define MUI_LANGDLL_REGISTRY_KEY "Software\${PRODUCT_NAME}"
!define MUI_LANGDLL_REGISTRY_VALUENAME "Installer Language"

;#####   Pages   ######
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "../../LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_STARTMENU Application $STARTMENU_FOLDER
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

;#####   Languages   ######
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"

Function .onInit
  !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Function un.onInit
  !insertmacro MUI_UNGETLANGUAGE
FunctionEnd

;#####   Installer Section   ######
Section "Install"
  SetShellVarContext all
  SetRegView 64

  SetOutPath "$INSTDIR"

  ;### Copy the materialized product ###
  File /r "${PRODUCT_DIR}/*"

  ;### Uninstaller ###
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ;### Start Menu and Desktop Shortcuts ###
  !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
    CreateDirectory "$SMPROGRAMS\$STARTMENU_FOLDER"
    CreateShortcut "$SMPROGRAMS\$STARTMENU_FOLDER\${PRODUCT_NAME}.lnk" "$INSTDIR\${PRODUCT_EXE}"
    CreateShortcut "$SMPROGRAMS\$STARTMENU_FOLDER\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
  !insertmacro MUI_STARTMENU_WRITE_END

  CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\${PRODUCT_EXE}"

  ;### Add or Remove Programs entry ###
  WriteRegStr HKLM "Software\${PRODUCT_NAME}" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "DisplayName" "${PRODUCT_NAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "Publisher" "${PRODUCT_PUBLISHER}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "DisplayIcon" "$\"$INSTDIR\${PRODUCT_EXE}$\""
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" "NoRepair" 1

  ;### Register the feed protocol ###
  WriteRegStr HKCR "feed" "" "URL:feed Protocol"
  WriteRegStr HKCR "feed" "URL Protocol" ""
  WriteRegStr HKCR "feed\DefaultIcon" "" "$\"$INSTDIR\${PRODUCT_EXE}$\""
  WriteRegStr HKCR "feed\shell\open\command" "" "$\"$INSTDIR\${PRODUCT_EXE}$\" $\"%1$\""
SectionEnd

;#####   Uninstaller Section   ######
Section "Uninstall"
  SetShellVarContext all
  SetRegView 64

  ;### Shortcuts ###
  !insertmacro MUI_STARTMENU_GETFOLDER Application $MUI_TEMP
  Delete "$SMPROGRAMS\$MUI_TEMP\${PRODUCT_NAME}.lnk"
  Delete "$SMPROGRAMS\$MUI_TEMP\Uninstall.lnk"
  RMDir "$SMPROGRAMS\$MUI_TEMP"
  Delete "$DESKTOP\${PRODUCT_NAME}.lnk"

  ;### Registry ###
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
  DeleteRegKey HKLM "Software\${PRODUCT_NAME}"

  ;### Remove the feed protocol only if it still points at this installation ###
  ReadRegStr $0 HKCR "feed\shell\open\command" ""
  StrCmp $0 "$\"$INSTDIR\${PRODUCT_EXE}$\" $\"%1$\"" 0 feedProtocolKept
    DeleteRegKey HKCR "feed"
  feedProtocolKept:

  ;### Program files ###
  RMDir /r "$INSTDIR"
SectionEnd
