#!/usr/bin/env ruby
# Script to automate iOS widget extension setup using xcodeproj gem
# Run on macOS (e.g., GitHub Actions macOS runner)

require 'xcodeproj'
require 'fileutils'

PROJECT_PATH = 'ios/Runner.xcodeproj'
WIDGET_NAME = 'BusWidget'
APP_GROUP_ID = 'group.com.oasth.widget'
BUNDLE_ID_PREFIX = 'com.oasth.live'

puts "🔧 Setting up iOS Widget Extension..."

# Open the Xcode project
project = Xcodeproj::Project.open(PROJECT_PATH)
puts "✓ Opened project: #{PROJECT_PATH}"

# Create widget extension target if it doesn't exist
widget_target = project.targets.find { |t| t.name == WIDGET_NAME }

if widget_target.nil?
  puts "Creating widget extension target: #{WIDGET_NAME}"
  
  # Create the widget extension target
  widget_target = project.new_target(
    :app_extension,
    WIDGET_NAME,
    :ios,
    '14.0'
  )
  
  # Set bundle identifier
  widget_target.build_configurations.each do |config|
    config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = "#{BUNDLE_ID_PREFIX}.#{WIDGET_NAME}"
    config.build_settings['INFOPLIST_FILE'] = "#{WIDGET_NAME}/Info.plist"
    config.build_settings['CODE_SIGN_STYLE'] = 'Automatic'
    config.build_settings['SWIFT_VERSION'] = '5.0'
    config.build_settings['TARGETED_DEVICE_FAMILY'] = '1,2'
    config.build_settings['ASSETCATALOG_COMPILER_WIDGET_BACKGROUND_COLOR_NAME'] = 'WidgetBackground'
    config.build_settings['LD_RUNPATH_SEARCH_PATHS'] = '$(inherited) @executable_path/Frameworks @executable_path/../../Frameworks'
  end
  
  puts "✓ Created widget target with bundle ID: #{BUNDLE_ID_PREFIX}.#{WIDGET_NAME}"
else
  puts "✓ Widget target already exists"
end

# Create widget group in project
widget_group = project.main_group.find_subpath(WIDGET_NAME, true)
widget_group.set_source_tree('<group>')

# Add Swift files to widget target
swift_files = [
  "#{WIDGET_NAME}/BusWidget.swift",
  "#{WIDGET_NAME}/MinimalBusWidget.swift"
]

swift_files.each do |file_path|
  if File.exist?("ios/#{file_path}")
    file_ref = widget_group.new_file("../#{file_path}")
    widget_target.add_file_references([file_ref])
    puts "✓ Added #{file_path} to widget target"
  else
    puts "⚠ File not found: #{file_path}"
  end
end

# Create Info.plist for widget
info_plist_path = "ios/#{WIDGET_NAME}/Info.plist"
unless File.exist?(info_plist_path)
  info_plist = <<~PLIST
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>CFBundleDevelopmentRegion</key>
        <string>$(DEVELOPMENT_LANGUAGE)</string>
        <key>CFBundleDisplayName</key>
        <string>OASTH Live Widget</string>
        <key>CFBundleExecutable</key>
        <string>$(EXECUTABLE_NAME)</string>
        <key>CFBundleIdentifier</key>
        <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
        <key>CFBundleInfoDictionaryVersion</key>
        <string>6.0</string>
        <key>CFBundleName</key>
        <string>$(PRODUCT_NAME)</string>
        <key>CFBundlePackageType</key>
        <string>$(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
        <key>CFBundleShortVersionString</key>
        <string>1.0</string>
        <key>CFBundleVersion</key>
        <string>1</string>
        <key>NSExtension</key>
        <dict>
            <key>NSExtensionPointIdentifier</key>
            <string>com.apple.widgetkit-extension</string>
        </dict>
    </dict>
    </plist>
  PLIST
  
  File.write(info_plist_path, info_plist)
  
  # Add Info.plist to project
  plist_ref = widget_group.new_file("../#{WIDGET_NAME}/Info.plist")
  puts "✓ Created Info.plist for widget"
end

# Add widget as dependency to main app
main_target = project.targets.find { |t| t.name == 'Runner' }
if main_target
  # Add widget to embed extensions phase
  embed_phase = main_target.build_phases.find { |p| p.is_a?(Xcodeproj::Project::Object::PBXCopyFilesBuildPhase) && p.name == 'Embed App Extensions' }
  
  if embed_phase.nil?
    embed_phase = main_target.new_copy_files_build_phase('Embed App Extensions')
    embed_phase.dst_subfolder_spec = '13'  # Plugins folder
  end
  
  # Add dependency
  main_target.add_dependency(widget_target)
  puts "✓ Added widget as dependency to main app"
end

# Configure App Groups entitlement for both targets
def add_app_group_capability(target, app_group_id, project_path)
  entitlements_file = "#{target.name}.entitlements"
  entitlements_path = File.join(File.dirname(project_path), target.name, entitlements_file)
  
  # Create entitlements directory if needed
  FileUtils.mkdir_p(File.dirname(entitlements_path))
  
  # Create entitlements file
  entitlements = <<~ENTITLEMENTS
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>com.apple.security.application-groups</key>
        <array>
            <string>#{app_group_id}</string>
        </array>
    </dict>
    </plist>
  ENTITLEMENTS
  
  File.write(entitlements_path, entitlements)
  
  # Update build settings
  target.build_configurations.each do |config|
    config.build_settings['CODE_SIGN_ENTITLEMENTS'] = "#{target.name}/#{entitlements_file}"
  end
  
  puts "✓ Configured App Group #{app_group_id} for #{target.name}"
end

# Add App Groups to both main app and widget
[main_target, widget_target].compact.each do |target|
  add_app_group_capability(target, APP_GROUP_ID, PROJECT_PATH)
end

# Save the project
project.save
puts "✓ Saved project"

puts ""
puts "🎉 Widget extension setup complete!"
puts ""
puts "Summary:"
puts "  - Widget target: #{WIDGET_NAME}"
puts "  - Bundle ID: #{BUNDLE_ID_PREFIX}.#{WIDGET_NAME}"
puts "  - App Group: #{APP_GROUP_ID}"
puts ""
puts "Next: Run 'flutter build ios --no-codesign' to build"
